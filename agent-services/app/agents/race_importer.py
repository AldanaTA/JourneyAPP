"""
race_importer.py

Supervised import agent for the Journey `races` and `race_traits` Postgres tables.

Racial traits are regular rows in the `traits` table tagged with the `racial`
category, so they stay searchable in the trait list. They are created while
importing the race. The import flow is:
- Split source text into race blocks (name line followed by "Size:")
- Read the race fields (size, movement, languages, description) deterministically
- Split each race's "Traits:" section into trait blocks (reuses trait_importer)
- Link traits that already exist in the `traits` table by name
  (optionally overwrite them with the race's version)
- Parse traits that do not exist yet with the trait agent
- Return valid_races, invalid_races, new_traits, and updated_traits
- Insert only valid races using synchronous psycopg
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

from agents.job_importer import (
    INSERT_NEW_TRAIT_SQL,
    INSERT_TRAIT_CATEGORY_SQL,
    name_key,
    next_nonempty_index,
)
from agents.trait_importer import (
    DELETE_TRAIT_CATEGORIES_SQL,
    UPSERT_TRAIT_SQL,
    InvalidTrait,
    TraitBlock,
    TraitCategory,
    TraitRow,
    extract_traits_with_agent,
    normalize_database_url,
    split_trait_blocks,
)
from helpers.agent_helpers import extract_text, get_max_workers, normalize_text

__all__ = [
    "AgentRaceImportResult",
    "extract_races_with_agent",
    "extract_text",
    "insert_valid_races",
]


# ----------------------------
# Race size (mirrors the Postgres `size` enum in backend/schema/07-races.sql)
# ----------------------------

RaceSize = Literal["T", "S", "M", "L", "H", "C"]

SIZE_LOOKUP: dict[str, str] = {
    "t": "T", "tiny": "T",
    "s": "S", "small": "S",
    "m": "M", "medium": "M",
    "l": "L", "large": "L",
    "h": "H", "huge": "H",
    "c": "C", "colossal": "C", "gargantuan": "C",
}

DEFAULT_MOVEMENT = 30
DEFAULT_LANGUAGES = "Common"


def parse_size(value: str | None) -> str | None:
    """Reads the first word of a size value, e.g. "M", "Medium", or "Medium (5ft tall)"."""
    if not value:
        return None
    match = re.match(r"\s*([A-Za-z]+)", value)
    return SIZE_LOOKUP.get(match.group(1).lower()) if match else None


# ----------------------------
# Pydantic models
# ----------------------------

class RaceTraitRef(BaseModel):
    model_config = ConfigDict(extra="forbid")

    trait_name: str
    sort_order: int = Field(ge=0)
    # "existing" = linked as-is, "new" = created on commit, "updated" = overwritten on commit.
    status: Literal["existing", "new", "updated"]


class RaceRow(BaseModel):
    model_config = ConfigDict(extra="forbid")

    race_name: str = Field(min_length=1)
    race_size: RaceSize
    movement: int = Field(ge=0)
    languages: str = Field(min_length=1)
    race_description: str | None = None
    traits: list[RaceTraitRef] = Field(default_factory=list)
    # Non-blocking notes for the preview (defaults applied, trait text differences, ...).
    warnings: list[str] = Field(default_factory=list)

    @field_validator("race_name", "languages", "race_description", mode="before")
    @classmethod
    def clean_strings(cls, value: Any) -> Any:
        if value is None:
            return None
        return str(value).replace("\x00", "").strip()


class InvalidRace(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source_text: str
    errors: list[str]


class AgentRaceImportResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    valid_races: list[RaceRow]
    invalid_races: list[InvalidRace]
    # Traits that do not exist yet; inserted on commit.
    new_traits: list[TraitRow]
    # Existing traits that will be overwritten (only with update_existing_traits=True).
    updated_traits: list[TraitRow]
    # Traits that could not be parsed by the trait agent.
    invalid_traits: list[InvalidTrait]


# ----------------------------
# Race block splitting
# ----------------------------

@dataclass
class RaceBlock:
    index: int
    name: str
    text: str
    size_raw: str | None = None
    movement_raw: str | None = None
    languages_raw: str | None = None
    description: str | None = None
    has_traits_header: bool = False
    trait_blocks: list[TraitBlock] = field(default_factory=list)


RACE_NAME_LABEL = re.compile(r"(?i)^race(?:\s+name)?\s*:\s*(.*)$")
# A race header's Size value is short ("M", "Medium"); long values are prose that happens to start with "Size:".
RACE_SIZE_LINE = re.compile(r"(?i)^size\s*:\s*\S.{0,24}$")
RACE_FIELD_LABEL = re.compile(
    r"(?i)^(size|movement|speed|languages?|description|race\s+description)\s*:\s*(.*)$"
)
TRAITS_HEADER = re.compile(r"(?i)^(?:racial\s+|race\s+)?traits\s*:\s*$")

FIELD_KEYS = {
    "size": "size",
    "movement": "movement",
    "speed": "movement",
    "language": "languages",
    "languages": "languages",
    "description": "description",
    "race description": "description",
}


def is_possible_race_name(line: str) -> bool:
    cleaned = line.strip()

    if not cleaned or len(cleaned) > 100:
        return False

    if not re.search(r"[A-Za-z]", cleaned):
        return False

    # Allows "Race: Name" / "Race Name: Name" but blocks normal label lines.
    if ":" in cleaned and not RACE_NAME_LABEL.match(cleaned):
        return False

    return True


def extract_race_name_from_line(line: str) -> str:
    match = RACE_NAME_LABEL.match(line.strip())
    if match:
        return match.group(1).strip()
    return line.strip()


def find_race_starts(lines: list[str]) -> list[int]:
    """
    A race starts at a name line whose next non-empty line is a short "Size:" value.

    Intro text, headers, and trait blocks never match: trait names are
    followed by "LVL:", and prose is not followed by a "Size:" label.
    """
    starts: list[int] = []

    for i, line in enumerate(lines):
        if not is_possible_race_name(line):
            continue

        nxt = next_nonempty_index(lines, i + 1)
        if nxt is not None and RACE_SIZE_LINE.match(lines[nxt].strip()):
            starts.append(i)

    return starts


def parse_race_block(index: int, block_lines: list[str]) -> RaceBlock:
    block = RaceBlock(
        index=index,
        name=extract_race_name_from_line(block_lines[0]),
        text="\n".join(block_lines).strip(),
    )

    values: dict[str, list[str]] = {}
    current: str | None = None
    traits_start: int | None = None

    for i, line in enumerate(block_lines[1:], start=1):
        stripped = line.strip()

        if TRAITS_HEADER.match(stripped):
            traits_start = i + 1
            break

        match = RACE_FIELD_LABEL.match(stripped)
        if match:
            current = FIELD_KEYS[re.sub(r"\s+", " ", match.group(1).lower())]
            values.setdefault(current, [])
            if match.group(2).strip():
                values[current].append(match.group(2).strip())
            continue

        # Wrapped lines (e.g. a multi-line description) continue the previous label.
        if current and stripped:
            values[current].append(stripped)

    def joined(key: str) -> str | None:
        text = " ".join(values.get(key, [])).strip()
        return text or None

    block.size_raw = joined("size")
    block.movement_raw = joined("movement")
    block.languages_raw = joined("languages")
    block.description = joined("description")

    if traits_start is not None:
        block.has_traits_header = True
        block.trait_blocks = split_trait_blocks("\n".join(block_lines[traits_start:]))

    return block


def split_race_blocks(raw_text: str) -> list[RaceBlock]:
    text = normalize_text(raw_text)
    lines = [line.rstrip() for line in text.split("\n")]
    starts = find_race_starts(lines)

    blocks: list[RaceBlock] = []

    for number, start in enumerate(starts, start=1):
        end = starts[number] if number < len(starts) else len(lines)
        blocks.append(parse_race_block(number, lines[start:end]))

    return blocks


# ----------------------------
# Field parsing / pre-validation
# ----------------------------

def parse_race_fields(block: RaceBlock) -> tuple[dict[str, Any], list[str], list[str]]:
    """Returns (fields, errors, warnings) for the race-level columns."""
    errors: list[str] = []
    warnings: list[str] = []

    if not block.name:
        errors.append("Missing race name.")

    size = parse_size(block.size_raw)
    if size is None:
        errors.append(
            f"Size must be one of T, S, M, L, H, C (or Tiny ... Colossal), got: {block.size_raw!r}."
        )

    movement = DEFAULT_MOVEMENT
    if block.movement_raw is None:
        warnings.append(f"Missing Movement; defaulted to {DEFAULT_MOVEMENT}ft.")
    else:
        match = re.search(r"\d+", block.movement_raw)
        if match is None:
            errors.append(f"Movement must contain a number, got: {block.movement_raw!r}.")
        else:
            movement = int(match.group(0))
            if not re.fullmatch(r"\d+\s*(?:ft\.?|feet)?", block.movement_raw.strip(), re.IGNORECASE):
                warnings.append(
                    f"Movement {block.movement_raw!r} has extra detail; only {movement}ft is stored."
                )

    languages = block.languages_raw
    if not languages:
        languages = DEFAULT_LANGUAGES
        warnings.append(f"Missing Languages; defaulted to {DEFAULT_LANGUAGES!r}.")

    if not block.has_traits_header:
        warnings.append("No 'Traits:' header found; race has no traits.")
    elif not block.trait_blocks:
        warnings.append("'Traits:' header found but no trait blocks (name line followed by 'LVL:').")

    fields = {
        "race_name": block.name,
        "race_size": size,
        "movement": movement,
        "languages": languages,
        "race_description": block.description,
    }
    return fields, errors, warnings


EFFECT_LABEL = re.compile(r"(?i)^effect\s*:\s*(.*)$")
LVL_UP_LABEL = re.compile(r"(?i)^LVL\s*UP\s*:")


def block_effect_text(block: TraitBlock) -> str:
    """The multi-line Effect text of a trait block (up to the "LVL UP:" line)."""
    parts: list[str] = []
    in_effect = False

    for line in block.text.split("\n"):
        stripped = line.strip()
        match = EFFECT_LABEL.match(stripped)
        if match and not in_effect:
            in_effect = True
            parts.append(match.group(1))
            continue
        if in_effect:
            if LVL_UP_LABEL.match(stripped):
                break
            parts.append(stripped)

    return " ".join(parts)


def comparable_text(text: str | None) -> str:
    """Whitespace/quote/case-insensitive form used to tell whether two trait texts differ."""
    return re.sub(r"\s+", " ", name_key(text or ""))


# ----------------------------
# Existing trait lookup
# ----------------------------

def fetch_existing_traits(database_url: str) -> dict[str, tuple[str, str]]:
    """Returns {name_key: (trait_name, trait_effect)} for every trait in the database."""
    import psycopg

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT trait_name, trait_effect FROM traits;")
            return {name_key(name): (name, effect) for name, effect in cur.fetchall()}


# ----------------------------
# Extraction pipeline
# ----------------------------

def extract_races_with_agent(
    raw_text: str,
    model: str,
    database_url: str,
    max_batch_chars: int,
    update_existing_traits: bool = False,
    max_workers: int = get_max_workers(),
) -> tuple[AgentRaceImportResult, dict[str, Any]]:
    all_blocks = split_race_blocks(raw_text)

    metadata: dict[str, Any] = {
        "race_blocks_found": len(all_blocks),
        "pre_validation_invalid": 0,
        "trait_refs_found": 0,
        "existing_traits_linked": 0,
        "new_traits_parsed": 0,
        "updated_traits_parsed": 0,
        "duplicate_count": 0,
    }

    if not all_blocks:
        return (
            AgentRaceImportResult(
                valid_races=[],
                invalid_races=[
                    InvalidRace(
                        source_text=raw_text[:4000],
                        errors=["No race blocks found. Expected a race name line followed by 'Size:'."],
                    )
                ],
                new_traits=[],
                updated_traits=[],
                invalid_traits=[],
            ),
            metadata,
        )

    invalid_races: list[InvalidRace] = []
    candidates: list[tuple[RaceBlock, dict[str, Any], list[str]]] = []
    seen_race_names: set[str] = set()

    for block in all_blocks:
        fields, errors, warnings = parse_race_fields(block)

        if errors:
            invalid_races.append(InvalidRace(source_text=block.text, errors=errors))
            continue

        key = name_key(block.name)
        if key in seen_race_names:
            metadata["duplicate_count"] += 1
            continue

        seen_race_names.add(key)
        candidates.append((block, fields, warnings))

    metadata["pre_validation_invalid"] = len(invalid_races)
    metadata["trait_refs_found"] = sum(len(block.trait_blocks) for block, _, _ in candidates)

    # First definition of each trait in this file wins; later differing copies get a warning.
    first_definition: dict[str, tuple[TraitBlock, str]] = {}
    for block, _, warnings in candidates:
        for trait in block.trait_blocks:
            key = name_key(trait.name)
            if key not in first_definition:
                first_definition[key] = (trait, block.name)
            elif comparable_text(block_effect_text(trait)) != comparable_text(
                block_effect_text(first_definition[key][0])
            ):
                warnings.append(
                    f"Trait {trait.name!r} is also defined by race {first_definition[key][1]!r} "
                    "in this file with a different effect; the first definition is used."
                )

    existing = fetch_existing_traits(database_url)

    # Parse missing traits, plus existing ones when they should be overwritten.
    to_parse = {
        key: trait
        for key, (trait, _) in first_definition.items()
        if key not in existing or update_existing_traits
    }

    parsed: dict[str, TraitRow] = {}
    invalid_traits: list[InvalidTrait] = []

    if to_parse:
        trait_result, trait_metadata = extract_traits_with_agent(
            raw_text="\n\n".join(block.text for block in to_parse.values()),
            model=model,
            max_batch_chars=max_batch_chars,
            max_workers=max_workers,
        )
        parsed = {name_key(trait.trait_name): trait for trait in trait_result.valid_traits}
        invalid_traits = list(trait_result.invalid_traits)
        metadata["trait_import"] = trait_metadata

    valid_races: list[RaceRow] = []
    used_new: dict[str, TraitRow] = {}
    used_updated: dict[str, TraitRow] = {}
    linked_existing: set[str] = set()

    for block, fields, warnings in candidates:
        refs: list[RaceTraitRef] = []
        errors: list[str] = []
        seen_in_race: set[str] = set()

        for trait in block.trait_blocks:
            key = name_key(trait.name)

            # race_traits has PK (race_id, trait_id): a trait can appear once per race.
            if key in seen_in_race:
                warnings.append(f"Trait {trait.name!r} is listed more than once; kept the first.")
                continue
            seen_in_race.add(key)

            if key in existing and not update_existing_traits:
                db_name, db_effect = existing[key]
                linked_existing.add(key)
                refs.append(RaceTraitRef(trait_name=db_name, sort_order=len(refs), status="existing"))

                if comparable_text(db_effect) != comparable_text(block_effect_text(trait)):
                    warnings.append(
                        f"Trait {db_name!r} already exists with a different effect; the existing trait "
                        "is linked unchanged. Use update_existing_traits=true to overwrite it."
                    )
            elif key in parsed:
                status = "updated" if key in existing else "new"
                (used_updated if status == "updated" else used_new)[key] = parsed[key]
                refs.append(RaceTraitRef(trait_name=parsed[key].trait_name, sort_order=len(refs), status=status))
            else:
                errors.append(f"Trait {trait.name!r} could not be parsed.")

        if errors:
            invalid_races.append(InvalidRace(source_text=block.text, errors=errors))
            continue

        valid_races.append(RaceRow(**fields, traits=refs, warnings=warnings))

    metadata["existing_traits_linked"] = len(linked_existing)
    metadata["new_traits_parsed"] = len(used_new)
    metadata["updated_traits_parsed"] = len(used_updated)

    return (
        AgentRaceImportResult(
            valid_races=valid_races,
            invalid_races=invalid_races,
            new_traits=list(used_new.values()),
            updated_traits=list(used_updated.values()),
            invalid_traits=invalid_traits,
        ),
        metadata,
    )


# ----------------------------
# Database insert
# ----------------------------

UPSERT_RACE_SQL = """
INSERT INTO races (source_id, race_name, race_size, movement, languages, race_description)
VALUES (
    %(source_id)s,
    %(race_name)s,
    %(race_size)s::size,
    %(movement)s,
    %(languages)s,
    %(race_description)s
)
ON CONFLICT (name_key)
DO UPDATE SET
    source_id = EXCLUDED.source_id,
    race_name = EXCLUDED.race_name,
    race_size = EXCLUDED.race_size,
    movement = EXCLUDED.movement,
    languages = EXCLUDED.languages,
    race_description = EXCLUDED.race_description,
    updated_at = NOW()
RETURNING id;
"""

DELETE_RACE_TRAITS_SQL = "DELETE FROM race_traits WHERE race_id = %(race_id)s;"

INSERT_RACE_TRAIT_SQL = """
INSERT INTO race_traits (race_id, trait_id, trait_name, sort_order)
VALUES (%(race_id)s, %(trait_id)s, %(trait_name)s, %(sort_order)s);
"""


def insert_trait_categories(cur: Any, trait_id: Any, categories: list[str]) -> None:
    """Inserts the trait's categories plus "racial" (duplicates are ignored)."""
    for category in [*categories, TraitCategory.racial.value]:
        cur.execute(INSERT_TRAIT_CATEGORY_SQL, {"trait_id": trait_id, "category": category})


def insert_valid_races(
    result: AgentRaceImportResult,
    database_url: str,
    source_id: str,
) -> dict[str, list[str]]:
    """
    Insert new/updated racial traits and valid races in a single transaction.

    Every trait linked to a race is tagged with the "racial" category. Re-importing
    a race (matched on races.name_key) replaces its fields and trait links.

    Returns {"race_ids": [...], "trait_ids": [...]} where trait_ids are traits
    created or overwritten by this import.
    """
    try:
        import psycopg
    except ImportError as exc:
        raise RuntimeError("Missing dependency. Install with: pip install psycopg[binary]") from exc

    race_ids: list[str] = []
    trait_ids: list[str] = []

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        with conn.cursor() as cur:
            # 1. Create traits that do not exist yet (never overwrite here).
            for trait in result.new_traits:
                trait_data = trait.model_dump(mode="json")
                categories = trait_data.pop("categories", [])

                cur.execute(INSERT_NEW_TRAIT_SQL, {"source_id": source_id, **trait_data})
                row = cur.fetchone()
                if row is None:
                    # Created concurrently since preview; keep the existing row.
                    continue

                insert_trait_categories(cur, row[0], categories)
                trait_ids.append(str(row[0]))

            # 2. Overwrite existing traits with the race's version (update_existing_traits=True).
            for trait in result.updated_traits:
                trait_data = trait.model_dump(mode="json")
                categories = trait_data.pop("categories", [])

                cur.execute(UPSERT_TRAIT_SQL, {"source_id": source_id, **trait_data})
                trait_id = cur.fetchone()[0]

                cur.execute(DELETE_TRAIT_CATEGORIES_SQL, {"trait_id": trait_id})
                insert_trait_categories(cur, trait_id, categories)
                trait_ids.append(str(trait_id))

            # 3. Look up every trait by name.
            cur.execute("SELECT id, trait_name FROM traits;")
            traits_by_key = {name_key(name): (trait_id, name) for trait_id, name in cur.fetchall()}

            # 4. Upsert races and replace their trait links.
            for race in result.valid_races:
                cur.execute(
                    UPSERT_RACE_SQL,
                    {
                        "source_id": source_id,
                        **race.model_dump(mode="json", exclude={"traits", "warnings"}),
                    },
                )
                race_id = cur.fetchone()[0]

                cur.execute(DELETE_RACE_TRAITS_SQL, {"race_id": race_id})

                for ref in race.traits:
                    found = traits_by_key.get(name_key(ref.trait_name))

                    if found is None:
                        raise RuntimeError(
                            f"Race {race.race_name!r} references trait {ref.trait_name!r}, "
                            "which does not exist in the database."
                        )

                    trait_id, trait_name = found
                    cur.execute(
                        INSERT_RACE_TRAIT_SQL,
                        {
                            "race_id": race_id,
                            "trait_id": trait_id,
                            "trait_name": trait_name,
                            "sort_order": ref.sort_order,
                        },
                    )
                    # Existing traits linked to a race become searchable as racial too.
                    insert_trait_categories(cur, trait_id, [])

                race_ids.append(str(race_id))

        conn.commit()

    return {"race_ids": race_ids, "trait_ids": trait_ids}
