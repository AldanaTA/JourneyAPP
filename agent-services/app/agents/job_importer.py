"""
job_importer.py

Supervised import agent for the Journey `jobs`, `job_traits`, and
`job_category_assignments` Postgres tables.

Jobs are built from traits. The import flow is:
- Split source text into job blocks (name line followed by "Description:" / "Job Description:")
- Split each job's "Traits:" section into trait blocks (reuses trait_importer)
- Link traits that already exist in the `traits` table by name
- Optionally create traits that do not exist yet (parsed with the trait agent)
- Ask the agent to assign job categories
- Return valid_jobs, invalid_jobs, and new_traits
- Insert only valid jobs using synchronous psycopg
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

from openai import OpenAI
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from agents.trait_importer import (
    AgentTraitImportResult,
    InvalidTrait,
    TraitBlock,
    TraitRow,
    extract_traits_with_agent,
    normalize_database_url,
    split_trait_blocks,
)
from helpers.agent_helpers import (
    batch_by_char_count,
    extract_text,
    get_max_workers,
    normalize_text,
    run_threaded_batches,
)

__all__ = [
    "AgentJobImportResult",
    "extract_jobs_with_agent",
    "extract_text",
    "insert_valid_jobs",
]


# ----------------------------
# Job categories (mirrors the seed rows in backend/schema/04-jobs.sql)
# ----------------------------

class JobCategory(str, Enum):
    generalist = "Generalist"
    martial = "Martial"
    caster = "Caster"
    half_caster = "Half-Caster"
    ranged = "Ranged"
    defender = "Defender"
    support = "Support"
    saboteur = "Saboteur"
    scout = "Scout"
    social = "Social"
    crafter = "Crafter"
    companion = "Companion"
    nature = "Nature"
    divine = "Divine"
    occult = "Occult"
    specialist = "Specialist"


JOB_CATEGORY_VALUES = [category.value for category in JobCategory]
JOB_CATEGORY_LOOKUP = {value.lower(): value for value in JOB_CATEGORY_VALUES}
MAX_JOB_CATEGORIES = 3


def name_key(name: str) -> str:
    """Matching key for trait/job names. Mirrors trait_name_key, plus curly-quote folding."""
    return (
        name.replace("’", "'")
        .replace("‘", "'")
        .replace("“", '"')
        .replace("”", '"')
        .strip()
        .lower()
    )


# ----------------------------
# Pydantic models
# ----------------------------

class JobTraitRef(BaseModel):
    model_config = ConfigDict(extra="forbid")

    trait_name: str
    sort_order: int = Field(ge=0)
    # "existing" = already in the traits table, "new" = will be created on commit.
    status: str


class JobRow(BaseModel):
    model_config = ConfigDict(extra="forbid")

    job_name: str = Field(min_length=1)
    job_desc: str = Field(min_length=1)
    traits: list[JobTraitRef] = Field(min_length=1)
    categories: list[JobCategory] = Field(default_factory=list)
    primary_category: JobCategory = JobCategory.specialist

    @field_validator("job_name", "job_desc", mode="before")
    @classmethod
    def clean_strings(cls, value: Any) -> str:
        return str(value or "").replace("\x00", "").strip()

    @field_validator("categories", mode="before")
    @classmethod
    def clean_categories(cls, value: Any) -> list[str]:
        cleaned: list[str] = []

        for item in value or []:
            category = JOB_CATEGORY_LOOKUP.get(str(item).strip().lower())

            if category and category not in cleaned:
                cleaned.append(category)

        return cleaned

    @field_validator("primary_category", mode="before")
    @classmethod
    def clean_primary(cls, value: Any) -> str:
        return JOB_CATEGORY_LOOKUP.get(str(value or "").strip().lower(), JobCategory.specialist.value)

    @model_validator(mode="after")
    def finalize_categories(self):
        # Primary category always comes first and is always included.
        categories = [self.primary_category] + [
            category for category in self.categories if category != self.primary_category
        ]
        self.categories = categories[:MAX_JOB_CATEGORIES]
        return self


class InvalidJob(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source_text: str
    errors: list[str]


class AgentJobImportResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    valid_jobs: list[JobRow]
    invalid_jobs: list[InvalidJob]
    # Traits referenced by jobs that do not exist yet; inserted on commit.
    new_traits: list[TraitRow]
    # Traits that could not be parsed while creating missing traits.
    invalid_traits: list[InvalidTrait]


# ----------------------------
# Job block splitting
# ----------------------------

@dataclass
class JobBlock:
    index: int
    name: str
    text: str
    description: str | None
    trait_blocks: list[TraitBlock] = field(default_factory=list)


JOB_DESCRIPTION_LABEL = re.compile(r"(?i)^(?:job\s+)?description\s*:\s*(.*)$")
JOB_NAME_LABEL = re.compile(r"(?i)^job(?:\s+name)?\s*:\s*(.*)$")
TRAITS_HEADER = re.compile(r"(?i)^(?:job\s+)?traits\s*:\s*$")
TRAIT_LVL_LINE = re.compile(r"(?i)^LVL\s*:")


def is_possible_job_name(line: str) -> bool:
    cleaned = line.strip()

    if not cleaned or len(cleaned) > 100:
        return False

    if not re.search(r"[A-Za-z]", cleaned):
        return False

    # Allows "Job: Name" / "Job Name: Name" but blocks normal label lines.
    if ":" in cleaned and not JOB_NAME_LABEL.match(cleaned):
        return False

    return True


def extract_job_name_from_line(line: str) -> str:
    match = JOB_NAME_LABEL.match(line.strip())
    if match:
        return match.group(1).strip()
    return line.strip()


def next_nonempty_index(lines: list[str], start: int) -> int | None:
    for i in range(start, len(lines)):
        if lines[i].strip():
            return i
    return None


def find_job_starts(lines: list[str]) -> list[int]:
    """
    A job starts at a name line whose next non-empty line is
    "Description:" / "Job Description:", and which has a "Traits:" header
    before the first trait "LVL:" line.

    Trait blocks never match because their name line is followed by "LVL:".
    """
    starts: list[int] = []

    for i, line in enumerate(lines):
        if not is_possible_job_name(line):
            continue

        nxt = next_nonempty_index(lines, i + 1)
        if nxt is None or not JOB_DESCRIPTION_LABEL.match(lines[nxt].strip()):
            continue

        for j in range(nxt + 1, len(lines)):
            stripped = lines[j].strip()
            if TRAITS_HEADER.match(stripped):
                starts.append(i)
                break
            if TRAIT_LVL_LINE.match(stripped):
                break

    return starts


def parse_job_block(index: int, block_lines: list[str]) -> JobBlock:
    name = extract_job_name_from_line(block_lines[0])
    text = "\n".join(block_lines).strip()

    description_parts: list[str] = []
    traits_start: int | None = None
    in_description = False

    for i, line in enumerate(block_lines[1:], start=1):
        stripped = line.strip()

        if TRAITS_HEADER.match(stripped):
            traits_start = i + 1
            break

        match = JOB_DESCRIPTION_LABEL.match(stripped)
        if match and not in_description:
            in_description = True
            if match.group(1).strip():
                description_parts.append(match.group(1).strip())
            continue

        # Multi-line descriptions continue until the Traits: header.
        if in_description and stripped:
            description_parts.append(stripped)

    # Keep the source's line breaks (paragraphs).
    description = "\n".join(description_parts).strip() or None

    trait_blocks: list[TraitBlock] = []
    if traits_start is not None:
        trait_blocks = split_trait_blocks("\n".join(block_lines[traits_start:]))

    return JobBlock(
        index=index,
        name=name,
        text=text,
        description=description,
        trait_blocks=trait_blocks,
    )


def split_job_blocks(raw_text: str) -> list[JobBlock]:
    text = normalize_text(raw_text)
    lines = [line.rstrip() for line in text.split("\n")]
    starts = find_job_starts(lines)

    blocks: list[JobBlock] = []

    for number, start in enumerate(starts, start=1):
        end = starts[number] if number < len(starts) else len(lines)
        blocks.append(parse_job_block(number, lines[start:end]))

    return blocks


# ----------------------------
# Pre-validation
# ----------------------------

def prevalidate_job_block(block: JobBlock) -> list[str]:
    errors: list[str] = []

    if not block.name:
        errors.append("Missing job name.")

    if not block.description or block.description.strip().lower() in {"-", "none", "n/a"}:
        errors.append("Missing job description.")

    if not block.trait_blocks:
        errors.append(
            "No traits found. Expected a 'Traits:' header followed by trait blocks "
            "(trait name line followed by 'LVL:')."
        )

    return errors


# ----------------------------
# Existing trait lookup
# ----------------------------

def fetch_existing_trait_names(database_url: str) -> dict[str, str]:
    """Returns {name_key: trait_name} for every trait in the database."""
    import psycopg

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT trait_name FROM traits;")
            return {name_key(row[0]): row[0] for row in cur.fetchall()}


# ----------------------------
# Structured output schema (category assignment)
# ----------------------------

JOB_CATEGORY_JSON_SCHEMA: dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "jobs": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "job_index": {"type": "integer"},
                    "primary_category": {"type": "string", "enum": JOB_CATEGORY_VALUES},
                    "categories": {
                        "type": "array",
                        "items": {"type": "string", "enum": JOB_CATEGORY_VALUES},
                    },
                },
                "required": ["job_index", "primary_category", "categories"],
            },
        },
    },
    "required": ["jobs"],
}


AGENT_INSTRUCTIONS = f"""
You are a supervised data import agent for a tabletop RPG job (class) database.

You will receive pre-parsed jobs. Each job has an index, a name, a description,
and the traits that make up the job. Assign job categories to each job.

Return only JSON matching the provided schema, with one entry per job_index you received.

Job categories:
- Generalist: broad, flexible tools that do not commit to one narrow combat or magic identity.
- Martial: weapon combat, unarmed combat, physical durability, or direct fighting techniques.
- Caster: primarily MP powers, spellcasting, magical effects, or supernatural control.
- Half-Caster: blends martial combat with meaningful magical or supernatural abilities.
- Ranged: firearms, missile weapons, long-range attacks, or precision shooting.
- Defender: protection, shielding, durability, threat control, or keeping allies safe.
- Support: healing, recovery, buffs, ally movement, protection, or combat assistance.
- Saboteur: debuffs, control, disruption, stealth, confusion, or weakening enemies.
- Scout: mobility, exploration, stealth, tracking, travel, or objective-based play.
- Social: persuasion, performance, leadership, deception, investigation, or roleplay utility.
- Crafter: creating, modifying, preparing, enhancing, or efficiently using items.
- Companion: animal companions, summons, minions, spirits, undead, or controlled allies.
- Nature: animals, plants, primal magic, natural terrain, or wilderness themes.
- Divine: holy power, faith, miracles, anti-undead tools, or sacred protection.
- Occult: curses, pacts, forbidden magic, souls, blood magic, or unnatural powers.
- Specialist: a narrow or unusual identity that should be searchable separately from broad role categories.

Rules:
- Judge the job by what its traits actually do as a whole, not by single words.
- primary_category is the single best fit for the job's core identity.
- categories contains 1 to {MAX_JOB_CATEGORIES} categories and must include primary_category.
- Prefer fewer accurate categories over many weak ones.
- Use only these exact values: {", ".join(JOB_CATEGORY_VALUES)}
"""


def make_job_batch_text(blocks: list[JobBlock]) -> str:
    parts: list[str] = []

    for block in blocks:
        trait_text = "\n\n".join(trait.text for trait in block.trait_blocks)
        parts.append(
            f"--- JOB {block.index}: {block.name} ---\n"
            f"job_index: {block.index}\n"
            f"Description: {block.description}\n"
            f"Traits:\n{trait_text}"
        )

    return "\n\n".join(parts)


def categorize_jobs_batch_with_agent(
    blocks: list[JobBlock],
    model: str,
) -> dict[int, dict[str, Any]]:
    client = OpenAI()

    response = client.responses.create(
        model=model,
        instructions=AGENT_INSTRUCTIONS,
        input=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "input_text",
                        "text": (
                            "Assign categories to the following jobs.\n\n"
                            f"{make_job_batch_text(blocks)}"
                        ),
                    }
                ],
            }
        ],
        text={
            "format": {
                "type": "json_schema",
                "name": "job_category_result",
                "strict": True,
                "schema": JOB_CATEGORY_JSON_SCHEMA,
            }
        },
    )

    parsed = json.loads(response.output_text)
    return {int(entry["job_index"]): entry for entry in parsed.get("jobs", [])}


# ----------------------------
# Extraction pipeline
# ----------------------------

def resolve_missing_traits(
    job_blocks: list[JobBlock],
    existing: dict[str, str],
    model: str,
    max_batch_chars: int,
    max_workers: int,
) -> tuple[dict[str, TraitRow], list[InvalidTrait], dict[str, Any]]:
    """
    Parse traits that jobs reference but which are not in the database yet.

    Returns ({name_key: TraitRow}, invalid_traits, trait_metadata).
    """
    missing: dict[str, TraitBlock] = {}

    for job in job_blocks:
        for trait in job.trait_blocks:
            key = name_key(trait.name)
            if key not in existing and key not in missing:
                missing[key] = trait

    if not missing:
        return {}, [], {}

    trait_text = "\n\n".join(block.text for block in missing.values())
    trait_result, trait_metadata = extract_traits_with_agent(
        raw_text=trait_text,
        model=model,
        max_batch_chars=max_batch_chars,
        max_workers=max_workers,
    )

    parsed = {name_key(trait.trait_name): trait for trait in trait_result.valid_traits}
    return parsed, list(trait_result.invalid_traits), trait_metadata


def extract_jobs_with_agent(
    raw_text: str,
    model: str,
    database_url: str,
    max_batch_chars: int,
    create_missing_traits: bool = True,
    max_workers: int = get_max_workers(),
) -> tuple[AgentJobImportResult, dict[str, Any]]:
    all_blocks = split_job_blocks(raw_text)

    metadata: dict[str, Any] = {
        "job_blocks_found": len(all_blocks),
        "pre_validation_invalid": 0,
        "trait_refs_found": 0,
        "existing_traits_linked": 0,
        "missing_traits": 0,
        "new_traits_parsed": 0,
        "sent_to_agent": 0,
        "batches": 0,
        "thread_workers": 0,
        "duplicate_count": 0,
    }

    if not all_blocks:
        return (
            AgentJobImportResult(
                valid_jobs=[],
                invalid_jobs=[
                    InvalidJob(
                        source_text=raw_text[:4000],
                        errors=[
                            "No job blocks found. Expected a job name line, then "
                            "'Description:' or 'Job Description:', then a 'Traits:' header."
                        ],
                    )
                ],
                new_traits=[],
                invalid_traits=[],
            ),
            metadata,
        )

    invalid_jobs: list[InvalidJob] = []
    candidate_blocks: list[JobBlock] = []
    seen_job_names: set[str] = set()

    for block in all_blocks:
        errors = prevalidate_job_block(block)

        if errors:
            invalid_jobs.append(InvalidJob(source_text=block.text, errors=errors))
            continue

        key = name_key(block.name)
        if key in seen_job_names:
            metadata["duplicate_count"] += 1
            continue

        seen_job_names.add(key)
        candidate_blocks.append(block)

    metadata["pre_validation_invalid"] = len(invalid_jobs)
    metadata["trait_refs_found"] = sum(len(block.trait_blocks) for block in candidate_blocks)

    # Resolve traits: link existing ones, optionally parse missing ones.
    existing = fetch_existing_trait_names(database_url)

    new_traits_by_key: dict[str, TraitRow] = {}
    invalid_traits: list[InvalidTrait] = []

    if create_missing_traits:
        new_traits_by_key, invalid_traits, trait_metadata = resolve_missing_traits(
            candidate_blocks,
            existing,
            model=model,
            max_batch_chars=max_batch_chars,
            max_workers=max_workers,
        )
        if trait_metadata:
            metadata["trait_import"] = trait_metadata

    jobs_for_agent: list[JobBlock] = []
    job_trait_refs: dict[int, list[JobTraitRef]] = {}
    missing_keys: set[str] = set()
    linked_keys: set[str] = set()

    for block in candidate_blocks:
        refs: list[JobTraitRef] = []
        errors: list[str] = []
        seen_in_job: set[str] = set()

        for trait in block.trait_blocks:
            key = name_key(trait.name)

            # job_traits has PK (job_id, trait_id): a trait can appear once per job.
            if key in seen_in_job:
                continue
            seen_in_job.add(key)

            if key in existing:
                linked_keys.add(key)
                refs.append(JobTraitRef(trait_name=existing[key], sort_order=len(refs), status="existing"))
            elif key in new_traits_by_key:
                missing_keys.add(key)
                refs.append(
                    JobTraitRef(
                        trait_name=new_traits_by_key[key].trait_name,
                        sort_order=len(refs),
                        status="new",
                    )
                )
            else:
                missing_keys.add(key)
                if create_missing_traits:
                    errors.append(f"Trait {trait.name!r} is not in the database and could not be parsed.")
                else:
                    errors.append(f"Trait {trait.name!r} is not in the database.")

        if errors:
            invalid_jobs.append(InvalidJob(source_text=block.text, errors=errors))
            continue

        job_trait_refs[block.index] = refs
        jobs_for_agent.append(block)

    metadata["existing_traits_linked"] = len(linked_keys)
    metadata["missing_traits"] = len(missing_keys)
    metadata["new_traits_parsed"] = len(new_traits_by_key)
    metadata["sent_to_agent"] = len(jobs_for_agent)

    # Only keep new traits that are actually used by a valid job.
    used_new_keys = {
        name_key(ref.trait_name)
        for refs in job_trait_refs.values()
        for ref in refs
        if ref.status == "new"
    }
    new_traits = [trait for key, trait in new_traits_by_key.items() if key in used_new_keys]

    # Categorize valid jobs with the agent.
    batches = batch_by_char_count(
        jobs_for_agent,
        max_chars=max_batch_chars,
        text_getter=lambda block: block.text,
    )

    metadata["batches"] = len(batches)
    metadata["thread_workers"] = min(max_workers, len(batches)) if batches else 0

    category_results = run_threaded_batches(
        batches,
        worker=lambda batch: categorize_jobs_batch_with_agent(batch, model=model),
        max_workers=max_workers,
    )

    categories_by_index: dict[int, dict[str, Any]] = {}
    for result in category_results:
        categories_by_index.update(result)

    valid_jobs: list[JobRow] = []

    for block in jobs_for_agent:
        assigned = categories_by_index.get(block.index, {})

        valid_jobs.append(
            JobRow(
                job_name=block.name,
                job_desc=block.description or "",
                traits=job_trait_refs[block.index],
                categories=assigned.get("categories", []),
                primary_category=assigned.get("primary_category"),
            )
        )

    return (
        AgentJobImportResult(
            valid_jobs=valid_jobs,
            invalid_jobs=invalid_jobs,
            new_traits=new_traits,
            invalid_traits=invalid_traits,
        ),
        metadata,
    )


# ----------------------------
# Database insert
# ----------------------------

INSERT_NEW_TRAIT_SQL = """
INSERT INTO traits (
    source_id,
    trait_name,
    lvl,
    purchase_cost,
    lvl_up_cost,
    trait_desc,
    trait_uses,
    mp_cost,
    ep_cost,
    tp_cost,
    trait_effect,
    lvl_up_effect
)
VALUES (
    %(source_id)s,
    %(trait_name)s,
    %(lvl)s,
    %(purchase_cost)s,
    %(lvl_up_cost)s,
    %(trait_desc)s,
    %(trait_uses)s,
    %(mp_cost)s,
    %(ep_cost)s,
    %(tp_cost)s,
    %(trait_effect)s,
    %(lvl_up_effect)s
)
ON CONFLICT (trait_name_key) DO NOTHING
RETURNING id;
"""

INSERT_TRAIT_CATEGORY_SQL = """
INSERT INTO trait_categories (trait_id, category)
VALUES (%(trait_id)s, %(category)s::trait_category)
ON CONFLICT (trait_id, category) DO NOTHING;
"""

UPSERT_JOB_SQL = """
INSERT INTO jobs (source_id, name, description)
VALUES (%(source_id)s, %(name)s, %(description)s)
ON CONFLICT (name_key)
DO UPDATE SET
    source_id = EXCLUDED.source_id,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = NOW()
RETURNING id;
"""

DELETE_JOB_TRAITS_SQL = "DELETE FROM job_traits WHERE job_id = %(job_id)s;"

INSERT_JOB_TRAIT_SQL = """
INSERT INTO job_traits (job_id, trait_id, sort_order)
VALUES (%(job_id)s, %(trait_id)s, %(sort_order)s);
"""

DELETE_JOB_CATEGORIES_SQL = "DELETE FROM job_category_assignments WHERE job_id = %(job_id)s;"

INSERT_JOB_CATEGORY_SQL = """
INSERT INTO job_category_assignments (job_id, category_id, is_primary)
VALUES (%(job_id)s, %(category_id)s, %(is_primary)s);
"""


def insert_valid_jobs(
    result: AgentJobImportResult,
    database_url: str,
    source_id: str,
) -> dict[str, list[str]]:
    """
    Insert new traits and valid jobs in a single transaction.

    Existing traits are never modified. Re-importing a job (matched on the
    jobs.name_key column) replaces its description, traits, and categories.

    Returns {"job_ids": [...], "trait_ids": [...]} for inserted/updated rows.
    """
    try:
        import psycopg
    except ImportError as exc:
        raise RuntimeError("Missing dependency. Install with: pip install psycopg[binary]") from exc

    job_ids: list[str] = []
    new_trait_ids: list[str] = []

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        with conn.cursor() as cur:
            # 1. Create missing traits (never overwrite existing ones).
            for trait in result.new_traits:
                trait_data = trait.model_dump(mode="json")
                categories = trait_data.pop("categories", [])

                cur.execute(INSERT_NEW_TRAIT_SQL, {"source_id": source_id, **trait_data})
                row = cur.fetchone()

                if row is None:
                    # Created concurrently since preview; keep the existing row.
                    continue

                trait_id = row[0]
                new_trait_ids.append(str(trait_id))

                for category in categories:
                    cur.execute(INSERT_TRAIT_CATEGORY_SQL, {"trait_id": trait_id, "category": category})

            # 2. Build lookups for traits and job categories.
            cur.execute("SELECT id, trait_name FROM traits;")
            trait_ids_by_key = {name_key(name): trait_id for trait_id, name in cur.fetchall()}

            cur.execute("SELECT id, category FROM job_categories;")
            category_ids = {category.lower(): category_id for category_id, category in cur.fetchall()}

            # 3. Upsert jobs and replace their trait/category links.
            for job in result.valid_jobs:
                params = {
                    "source_id": source_id,
                    "name": job.job_name,
                    "description": job.job_desc,
                }

                cur.execute(UPSERT_JOB_SQL, params)
                job_id = cur.fetchone()[0]

                cur.execute(DELETE_JOB_TRAITS_SQL, {"job_id": job_id})

                for ref in job.traits:
                    trait_id = trait_ids_by_key.get(name_key(ref.trait_name))

                    if trait_id is None:
                        raise RuntimeError(
                            f"Job {job.job_name!r} references trait {ref.trait_name!r}, "
                            "which does not exist in the database."
                        )

                    cur.execute(
                        INSERT_JOB_TRAIT_SQL,
                        {"job_id": job_id, "trait_id": trait_id, "sort_order": ref.sort_order},
                    )

                cur.execute(DELETE_JOB_CATEGORIES_SQL, {"job_id": job_id})

                for category in job.categories:
                    category_id = category_ids.get(category.value.lower())

                    if category_id is None:
                        raise RuntimeError(
                            f"Job category {category.value!r} is missing from job_categories."
                        )

                    cur.execute(
                        INSERT_JOB_CATEGORY_SQL,
                        {
                            "job_id": job_id,
                            "category_id": category_id,
                            "is_primary": category == job.primary_category,
                        },
                    )

                job_ids.append(str(job_id))

        conn.commit()

    return {"job_ids": job_ids, "trait_ids": new_trait_ids}
