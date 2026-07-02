"""
trait_importer.py

Supervised import agent for the Journey `traits` and `trait_categories` Postgres tables.

Matches the power_importer.py pattern:
- Split source text into trait blocks
- Pre-validate obvious invalid blocks
- Send valid-looking blocks to the agent
- Return valid_traits and invalid_traits
- Insert only valid traits using synchronous psycopg
"""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from openai import OpenAI
from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator, model_validator

from helpers.agent_helpers import (
    batch_by_char_count,
    extract_text,
    normalize_text,
    run_threaded_batches,
    write_json_file,
)


# ----------------------------
# Your DB enum
# ----------------------------

class TraitCategory(str, Enum):
    damage = "damage"
    defense = "defense"
    healing_recovery = "healing_recovery"
    buff = "buff"
    debuff = "debuff"
    control = "control"
    mobility = "mobility"
    weapon = "weapon"
    magic = "magic"
    summoning_companions = "summoning_companions"
    transformation = "transformation"
    resource_economy = "resource_economy"
    skill = "skill"
    utility_exploration = "utility_exploration"
    crafting_items = "crafting_items"
    stealth_deception = "stealth_deception"
    social_roleplay = "social_roleplay"
    status_ailments = "status_ailments"
    environmental = "environmental"
    passive = "passive"
    declared = "declared"
    triggered = "triggered"
    other = "other"


TRAIT_CATEGORY_VALUES = [category.value for category in TraitCategory]
VALID_CATEGORIES = set(TRAIT_CATEGORY_VALUES)
MISSING_TEXT_VALUES = {"", "-", "none", "n/a", "null"}


# ----------------------------
# Pydantic validation models
# ----------------------------

class TraitRow(BaseModel):
    model_config = ConfigDict(extra="forbid")

    trait_name: str = Field(min_length=1)
    lvl: int = Field(ge=1)

    purchase_cost: int = Field(default=0, ge=0)
    lvl_up_cost: int = Field(default=0, ge=0)

    trait_desc: str = "None"
    trait_uses: int = Field(default=0, ge=0)

    mp_cost: int = Field(default=0, ge=0)
    ep_cost: int = Field(default=0, ge=0)
    tp_cost: int = Field(default=0, ge=0)

    trait_effect: str = Field(min_length=1)
    lvl_up_effect: str = "None"

    categories: list[TraitCategory] = Field(default_factory=list)

    @field_validator(
        "trait_name",
        "trait_desc",
        "trait_effect",
        "lvl_up_effect",
        mode="before",
    )
    @classmethod
    def clean_strings(cls, value: Any) -> str:
        if value is None:
            return "None"

        text = str(value).strip()
        return text if text else "None"

    @field_validator(
        "lvl",
        "purchase_cost",
        "lvl_up_cost",
        "trait_uses",
        "mp_cost",
        "ep_cost",
        "tp_cost",
        mode="before",
    )
    @classmethod
    def parse_ints(cls, value: Any) -> int:
        if value is None:
            return 0

        if isinstance(value, int):
            return max(value, 0)

        text = str(value).strip().lower()

        if text in MISSING_TEXT_VALUES:
            return 0

        match = re.search(r"\d+", text)
        return int(match.group(0)) if match else 0

    @field_validator("categories", mode="before")
    @classmethod
    def clean_categories(cls, value: Any) -> list[str]:
        if not value:
            return ["other"]

        cleaned: list[str] = []

        for item in value:
            category = str(item).strip().lower().replace(" ", "_").replace("-", "_")

            if category in VALID_CATEGORIES and category not in cleaned:
                cleaned.append(category)

        return cleaned or ["other"]

    @field_validator("trait_desc", "lvl_up_effect", mode="after")
    @classmethod
    def default_optional_text(cls, value: str) -> str:
        if value.strip().lower() in MISSING_TEXT_VALUES:
            return "None"
        return value

    @model_validator(mode="after")
    def apply_category_cleanup(self):
       self.categories = finalize_trait_categories(self)
       return self


class InvalidTrait(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source_text: str
    errors: list[str]


class AgentTraitImportResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    valid_traits: list[TraitRow]
    invalid_traits: list[InvalidTrait]


# ----------------------------
# Trait block splitting
# ----------------------------

@dataclass(frozen=True)
class TraitBlock:
    index: int
    name: str
    text: str


IGNORED_HEADER_LINES = {
    "traits",
    "racial traits",
    "race traits",
    "job traits",
    "utility traits",
    "combat traits",
    "passive traits",
    "active traits",
    "triggered traits",
    "magic traits",
    "weapon traits",
    "skill traits",
}


REQUIRED_TRAIT_LABELS = [
    "LVL:",
    "Effect:",
]


def is_possible_trait_name(line: str) -> bool:
    cleaned = line.strip()

    if not cleaned:
        return False

    if cleaned.lower() in IGNORED_HEADER_LINES:
        return False

    if len(cleaned) > 100:
        return False

    if not re.search(r"[A-Za-z]", cleaned):
        return False

    # Allows "Trait Name: Something" but blocks most normal label lines.
    if ":" in cleaned and not re.match(r"(?i)^trait name\s*:", cleaned):
        return False

    return True


def extract_trait_name_from_line(line: str) -> str:
    cleaned = line.strip()

    match = re.match(r"(?i)^trait name\s*:\s*(.*)$", cleaned)
    if match:
        name = match.group(1).strip()
        return name if name else "Unnamed Trait"

    return cleaned


def has_required_trait_labels(block_text: str) -> bool:
    return all(label.lower() in block_text.lower() for label in REQUIRED_TRAIT_LABELS)


def split_trait_blocks(raw_text: str) -> list[TraitBlock]:
    """
    Finds trait blocks by looking for:

        Some Trait Name
        LVL: ...

    Also supports:

        Trait Name: Some Trait Name
        LVL: ...

    Captures until the next trait name followed by LVL:.
    """
    text = normalize_text(raw_text)
    lines = [line.rstrip() for line in text.split("\n")]

    start_indexes: list[int] = []

    for i in range(len(lines) - 1):
        current_line = lines[i].strip()
        next_line = lines[i + 1].strip()

        if is_possible_trait_name(current_line) and re.match(r"(?i)^LVL\s*:", next_line):
            start_indexes.append(i)

    blocks: list[TraitBlock] = []

    for block_number, start in enumerate(start_indexes, start=1):
        end = start_indexes[block_number] if block_number < len(start_indexes) else len(lines)
        block_lines = lines[start:end]
        block_text = "\n".join(block_lines).strip()
        name = extract_trait_name_from_line(block_lines[0].strip())

        if has_required_trait_labels(block_text):
            blocks.append(TraitBlock(index=block_number, name=name, text=block_text))

    return blocks


# ----------------------------
# Pre-validation
# ----------------------------

def get_label_value(block_text: str, label: str) -> str | None:
    pattern = rf"(?im)^{re.escape(label)}\s*(.*)$"
    match = re.search(pattern, block_text)

    if not match:
        return None

    return match.group(1).strip()


def get_any_label_value(block_text: str, labels: list[str]) -> str | None:
    for label in labels:
        value = get_label_value(block_text, label)

        if value is not None:
            return value

    return None


def is_missing_text(value: Any) -> bool:
    if value is None:
        return True

    return str(value).strip().lower() in MISSING_TEXT_VALUES


def prevalidate_trait_block(block: TraitBlock) -> list[str]:
    """
    Catches cases that your DB schema cannot represent safely.

    Only reject things that would clearly break NOT NULL / INT columns
    or cause major data loss.
    """
    errors: list[str] = []

    lvl_raw = get_label_value(block.text, "LVL:")
    effect_raw = get_label_value(block.text, "Effect:")

    if is_missing_text(block.name) or block.name == "Unnamed Trait":
        errors.append("Missing trait name.")

    if not lvl_raw:
        errors.append("Missing LVL.")
    elif not re.fullmatch(r"\d+", lvl_raw):
        errors.append(f"LVL must be an integer, got: {lvl_raw!r}.")

    if not effect_raw:
        errors.append("Missing Effect.")

    return errors


def remove_invalid_blocks(blocks: list[TraitBlock]) -> tuple[list[TraitBlock], list[InvalidTrait]]:
    valid_for_agent: list[TraitBlock] = []
    invalid_traits: list[InvalidTrait] = []

    for block in blocks:
        errors = prevalidate_trait_block(block)

        if errors:
            invalid_traits.append(
                InvalidTrait(
                    source_text=block.text,
                    errors=errors,
                )
            )
        else:
            valid_for_agent.append(block)

    return valid_for_agent, invalid_traits


# ----------------------------
# Category inference fallback
# ----------------------------

def add_category(categories: list[TraitCategory], category: TraitCategory) -> None:
    if category not in categories:
        categories.append(category)


SEMANTIC_CATEGORIES = {
    TraitCategory.damage,
    TraitCategory.defense,
    TraitCategory.healing_recovery,
    TraitCategory.buff,
    TraitCategory.debuff,
    TraitCategory.control,
    TraitCategory.mobility,
    TraitCategory.weapon,
    TraitCategory.magic,
    TraitCategory.summoning_companions,
    TraitCategory.transformation,
    TraitCategory.resource_economy,
    TraitCategory.skill,
    TraitCategory.utility_exploration,
    TraitCategory.crafting_items,
    TraitCategory.stealth_deception,
    TraitCategory.social_roleplay,
    TraitCategory.status_ailments,
    TraitCategory.environmental,
    TraitCategory.other,
}

TIMING_CATEGORIES = {
    TraitCategory.passive,
    TraitCategory.declared,
    TraitCategory.triggered,
}


def unique_categories(categories: list[TraitCategory]) -> list[TraitCategory]:
    seen: set[TraitCategory] = set()
    result: list[TraitCategory] = []

    for category in categories:
        if category not in seen:
            seen.add(category)
            result.append(category)

    return result


def infer_primary_timing_category(trait: TraitRow) -> TraitCategory:
    """
    Returns exactly one of:
    - passive
    - declared
    - triggered

    Priority:
    triggered > declared > passive
    """
    text = f"""
    {trait.trait_name}
    {trait.trait_desc}
    {trait.trait_effect}
    {trait.lvl_up_effect}
    """.lower()

    triggered_patterns = [
        r"\bwhen\b",
        r"\bwhenever\b",
        r"\bafter\b",
        r"\bbefore\b",
        r"\bonce per round when\b",
        r"\bat the start of\b",
        r"\bat the end of\b",
        r"\bon a hit\b",
        r"\bon a miss\b",
        r"\bon a success\b",
        r"\bon a failure\b",
        r"\bon a straight success\b",
        r"\bon a superior success\b",
        r"\bif you are hit\b",
        r"\bif you take damage\b",
        r"\bif a creature\b",
        r"\bif an enemy\b",
        r"\bif an ally\b",
    ]

    declared_patterns = [
        r"\bactivate this trait\b",
        r"\byou can activate\b",
        r"\byou may activate\b",
        r"\byou can spend\b",
        r"\byou may spend\b",
        r"\bspend a use\b",
        r"\bspend one use\b",
        r"\bspend \d+ uses?\b",
        r"\bresource cost\b",
        r"\bas an action\b",
        r"\bduring your turn\b",
    ]

    has_triggered_language = any(
        re.search(pattern, text)
        for pattern in triggered_patterns
    )

    if has_triggered_language:
        return TraitCategory.triggered

    has_declared_cost = (
        trait.trait_uses > 0
        or trait.mp_cost > 0
        or trait.ep_cost > 0
        or trait.tp_cost > 0
    )

    has_declared_language = any(
        re.search(pattern, text)
        for pattern in declared_patterns
    )

    if has_declared_cost or has_declared_language:
        return TraitCategory.declared

    return TraitCategory.passive


def finalize_trait_categories(trait: TraitRow) -> list[TraitCategory]:
    """
    Trust the agent for semantic categories, but force exactly one timing category.

    Guarantees:
    - passive / declared / triggered are mutually exclusive
    - reaction_timing is never emitted
    """
    categories = unique_categories(list(trait.categories))

    semantic_categories = [
        category
        for category in categories
        if category in SEMANTIC_CATEGORIES
    ]

    useful_semantic = [
        category
        for category in semantic_categories
        if category != TraitCategory.other
    ]

    if useful_semantic:
        semantic_categories = useful_semantic

    if not semantic_categories:
        semantic_categories = [TraitCategory.other]

    semantic_categories = semantic_categories[:4]

    primary_timing = infer_primary_timing_category(trait)

    return unique_categories([
        *semantic_categories,
        primary_timing,
    ])

# ----------------------------
# Structured output schema
# ----------------------------

TRAIT_IMPORT_JSON_SCHEMA: dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "valid_traits": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "trait_name": {"type": "string"},
                    "lvl": {"type": "integer"},
                    "purchase_cost": {"type": "integer"},
                    "lvl_up_cost": {"type": "integer"},
                    "trait_desc": {"type": "string"},
                    "trait_uses": {"type": "integer"},
                    "mp_cost": {"type": "integer"},
                    "ep_cost": {"type": "integer"},
                    "tp_cost": {"type": "integer"},
                    "trait_effect": {"type": "string"},
                    "lvl_up_effect": {"type": "string"},
                    "categories": {
                        "type": "array",
                        "items": {
                            "type": "string",
                            "enum": TRAIT_CATEGORY_VALUES,
                        },
                    },
                },
                "required": [
                    "trait_name",
                    "lvl",
                    "purchase_cost",
                    "lvl_up_cost",
                    "trait_desc",
                    "trait_uses",
                    "mp_cost",
                    "ep_cost",
                    "tp_cost",
                    "trait_effect",
                    "lvl_up_effect",
                    "categories",
                ],
            },
        },
        "invalid_traits": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "source_text": {"type": "string"},
                    "errors": {
                        "type": "array",
                        "items": {"type": "string"},
                    },
                },
                "required": ["source_text", "errors"],
            },
        },
    },
    "required": ["valid_traits", "invalid_traits"],
}

CATEGORY_RULES = f"""
Category assignment rules:

Assign categories based on the trait's main gameplay purpose, not just individual words.

Use 1 to 3 semantic categories:
- damage: directly increases, deals, stores, reflects, converts, or modifies damage.
- defense: prevents harm, reduces damage, improves survivability, protects allies, grants resistance, armor, shields, THP, or defensive reactions.
- healing_recovery: restores HP, removes harm, regenerates, recovers from injury, or improves rest/recovery.
- buff: improves a creature's rolls, stats, actions, damage, movement, defenses, or effectiveness.
- debuff: worsens enemy rolls, stats, defenses, damage, movement, or effectiveness.
- control: restricts choices, denies actions, forces movement, applies prone/stun/restrain/unconscious, or manipulates positioning.
- mobility: improves movement, speed, climbing, swimming, jumping, flying, teleporting, or travel.
- weapon: mainly modifies weapon attacks, weapon properties, parry, block, melee, ranged, or martial combat.
- magic: mainly modifies magic, powers, MP, casting, spell-like effects, arcane/psychic/divine/cursed effects.
- summoning_companions: creates, controls, buffs, stores, commands, or interacts with summons, companions, undead, pets, familiars, or minions.
- transformation: changes form, body, shape, anatomy, creature type, or grants form-based effects.
- resource_economy: mainly changes MP, EP, TP, uses, costs, cooldowns, recovery, or resource conversion. Do not use this only because a trait has a purchase cost or normal Uses field.
- skill: mainly changes skill rolls, skill bonuses, training, proficiency, expertise, or roll conditions.
- utility_exploration: mainly helps travel, scouting, searching, survival, navigation, investigation, detection, or non-combat problem solving.
- crafting_items: mainly creates, improves, repairs, identifies, stores, enhances, or uses items, potions, tools, materials, or crafted objects.
- stealth_deception: mainly helps hiding, sneaking, disguise, misdirection, lying, infiltration, or avoiding detection.
- social_roleplay: mainly helps persuasion, intimidation, interrogation, charm, reputation, language, social scenes, or roleplay interaction.
- status_ailments: mainly applies, removes, resists, modifies, or interacts with named status ailments.
- environmental: mainly creates, changes, resists, or exploits terrain, weather, darkness, light, hazards, areas, or environmental conditions.
- other: only use when no other semantic category fits.

Timing category rules:
- passive: always-on trait with no activation and no trigger condition.
- declared: player chooses to activate it, spend a use, spend MP/EP/TP, or intentionally use the trait.
- triggered: happens because a condition occurs, such as when hit, on success, on failure, start of round, end of round, creature enters range, etc.

Important:
- Always include exactly one of passive, declared, or triggered.
- Do not assign categories based on a single generic word.
- Prefer fewer accurate categories over many weak categories.
- Use only these enum values:
{", ".join(TRAIT_CATEGORY_VALUES)}
"""

AGENT_INSTRUCTIONS = f"""
You are a supervised data import agent for a tabletop trait database.

You will receive pre-split trait blocks. Each block should represent exactly one trait.
Do not extract intro/rules text, category headers, examples, or section headers.

Return only JSON matching the provided schema.

Database columns:
- trait_name TEXT NOT NULL
- lvl INT NOT NULL
- purchase_cost INT NOT NULL
- lvl_up_cost INT NOT NULL
- trait_desc TEXT NOT NULL
- trait_uses INT DEFAULT 0
- mp_cost INT DEFAULT 0
- ep_cost INT DEFAULT 0
- tp_cost INT DEFAULT 0
- trait_effect TEXT NOT NULL
- lvl_up_effect TEXT DEFAULT 'None'

Category enum values:
{", ".join(TRAIT_CATEGORY_VALUES)}

Field mapping rules:
- The first name line maps to trait_name.
- "LVL:" maps to lvl.
- "Purchase Cost:" maps to purchase_cost.
- "LVL UP Cost:" maps to lvl_up_cost.
- "Description:" maps to trait_desc.
- "Effect:" maps to trait_effect.
- "LVL UP:" maps to lvl_up_effect.
- If Description is empty, "-", or "None", use "None".
- If LVL UP is empty, "-", or "None", use "None".
- Convert EXP costs to integers.
- Convert "None", "-", blank, or missing numeric values to 0.

Uses and resource parsing rules:
- "Uses: 3" means trait_uses=3.
- "Uses or Resource Cost: 3" means trait_uses=3.
- "Resource Cost: 15EP 2TP" means ep_cost=15 and tp_cost=2.
- "Resource Cost: 20MP 1TP" means mp_cost=20 and tp_cost=1.
- If a resource does not appear, use 0.
- If uses are "None", use 0.
- If a cost is variable or nonnumeric, set the numeric cost to 0 and preserve the detail in trait_effect.

{CATEGORY_RULES}

Invalid rules:
Put a block into invalid_traits when:
- trait_name is missing
- lvl is missing, "None", or not an integer
- trait_effect is missing
"""


# ----------------------------
# Agent batching
# ----------------------------

def make_block_batch_text(blocks: list[TraitBlock]) -> str:
    parts: list[str] = []

    for block in blocks:
        parts.append(f"--- TRAIT BLOCK {block.index}: {block.name} ---\n{block.text}")

    return "\n\n".join(parts)


def extract_traits_batch_with_agent(blocks: list[TraitBlock], model: str) -> AgentTraitImportResult:
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
                            "Extract the following pre-split trait blocks into database rows.\n\n"
                            f"{make_block_batch_text(blocks)}"
                        ),
                    }
                ],
            }
        ],
        text={
            "format": {
                "type": "json_schema",
                "name": "trait_import_result",
                "strict": True,
                "schema": TRAIT_IMPORT_JSON_SCHEMA,
            }
        },
    )

    raw_json = response.output_text
    parsed = json.loads(raw_json)

    return AgentTraitImportResult.model_validate(parsed)


def merge_results(results: list[AgentTraitImportResult]) -> AgentTraitImportResult:
    valid_traits: list[TraitRow] = []
    invalid_traits: list[InvalidTrait] = []

    for result in results:
        valid_traits.extend(result.valid_traits)
        invalid_traits.extend(result.invalid_traits)

    return AgentTraitImportResult(
        valid_traits=valid_traits,
        invalid_traits=invalid_traits,
    )


def dedupe_traits(result: AgentTraitImportResult) -> tuple[AgentTraitImportResult, int]:
    seen: set[tuple[str, int]] = set()
    unique_traits: list[TraitRow] = []
    duplicate_count = 0

    for trait in result.valid_traits:
        key = (trait.trait_name.strip().lower(), trait.lvl)

        if key in seen:
            duplicate_count += 1
            continue

        seen.add(key)
        unique_traits.append(trait)

    return (
        AgentTraitImportResult(
            valid_traits=unique_traits,
            invalid_traits=result.invalid_traits,
        ),
        duplicate_count,
    )


def extract_traits_with_agent(
    raw_text: str,
    model: str,
    max_batch_chars: int,
    max_workers: int = 4,
) -> tuple[AgentTraitImportResult, dict[str, Any]]:
    all_blocks = split_trait_blocks(raw_text)
    blocks_for_agent, pre_invalid = remove_invalid_blocks(all_blocks)

    metadata: dict[str, Any] = {
        "trait_blocks_found": len(all_blocks),
        "pre_validation_invalid": len(pre_invalid),
        "sent_to_agent": len(blocks_for_agent),
        "batches": 0,
        "thread_workers": 0,
        "duplicate_count": 0,
    }

    if not all_blocks:
        return (
            AgentTraitImportResult(
                valid_traits=[],
                invalid_traits=[
                    InvalidTrait(
                        source_text=raw_text[:4000],
                        errors=[
                            "No trait blocks found. Expected a trait name line followed by 'LVL:'."
                        ],
                    )
                ],
            ),
            metadata,
        )

    batches = batch_by_char_count(
        blocks_for_agent,
        max_chars=max_batch_chars,
        text_getter=lambda block: block.text,
    )

    metadata["batches"] = len(batches)
    metadata["thread_workers"] = min(max_workers, len(batches)) if batches else 0

    batch_results: list[AgentTraitImportResult] = []

    if pre_invalid:
        batch_results.append(
            AgentTraitImportResult(
                valid_traits=[],
                invalid_traits=pre_invalid,
            )
        )

    threaded_results = run_threaded_batches(
        batches,
        worker=lambda batch: extract_traits_batch_with_agent(batch, model=model),
        max_workers=max_workers,
    )

    batch_results.extend(threaded_results)

    merged_result = merge_results(batch_results)
    deduped_result, duplicate_count = dedupe_traits(merged_result)

    metadata["duplicate_count"] = duplicate_count

    return deduped_result, metadata


# ----------------------------
# Import preview output
# ----------------------------

def write_preview(
    result: AgentTraitImportResult,
    metadata: dict[str, Any],
    output_path: Path,
) -> None:
    preview = {
        "metadata": metadata,
        "valid_traits": [
            trait.model_dump(mode="json")
            for trait in result.valid_traits
        ],
        "invalid_traits": [
            invalid.model_dump(mode="json")
            for invalid in result.invalid_traits
        ],
    }

    write_json_file(preview, output_path)


# ----------------------------
# Database insert
# ----------------------------

INSERT_TRAIT_SQL = """
INSERT INTO traits (
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
RETURNING id;
"""


INSERT_TRAIT_CATEGORY_SQL = """
INSERT INTO trait_categories (
    trait_id,
    category
)
VALUES (
    %(trait_id)s,
    %(category)s::trait_category
)
ON CONFLICT (trait_id, category)
DO NOTHING;
"""


def normalize_database_url(database_url: str) -> str:
    return (
        database_url
        .replace("postgresql+asyncpg://", "postgresql://", 1)
        .replace("postgresql+psycopg://", "postgresql://", 1)
    )


def insert_valid_traits(
    result: AgentTraitImportResult,
    database_url: str,
) -> list[str]:
    try:
        import psycopg
    except ImportError as exc:
        raise RuntimeError("Missing dependency. Install with: pip install psycopg[binary]") from exc

    inserted_ids: list[str] = []

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        with conn.cursor() as cur:
            for trait in result.valid_traits:
                trait_data = trait.model_dump(mode="json")
                categories = trait_data.pop("categories", [])

                cur.execute(INSERT_TRAIT_SQL, trait_data)
                trait_id = cur.fetchone()[0]

                for category in categories:
                    cur.execute(
                        INSERT_TRAIT_CATEGORY_SQL,
                        {
                            "trait_id": trait_id,
                            "category": category,
                        },
                    )

                inserted_ids.append(str(trait_id))

        conn.commit()

    return inserted_ids


# ----------------------------
# Main CLI
# ----------------------------

def main() -> None:
    load_dotenv()

    parser = argparse.ArgumentParser(
        description="Import traits from a TXT, DOCX, or PDF file into a reviewable JSON preview."
    )
    parser.add_argument("file", help="Path to .txt, .docx, or .pdf file.")
    parser.add_argument(
        "--model",
        default=os.getenv("OPENAI_MODEL", "gpt-4.1-mini"),
        help="OpenAI model to use. Can also be set with OPENAI_MODEL.",
    )
    parser.add_argument(
        "--out",
        default="trait_import_preview.json",
        help="Where to save the reviewable JSON preview.",
    )
    parser.add_argument(
        "--max-batch-chars",
        type=int,
        default=12000,
        help="Approximate max characters sent to the model per batch.",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=4,
        help="Maximum number of trait batches to process concurrently.",
    )
    parser.add_argument(
        "--commit",
        action="store_true",
        help="Insert valid traits into Postgres after preview generation.",
    )

    args = parser.parse_args()

    source_path = Path(args.file)
    output_path = Path(args.out)

    if not source_path.exists():
        raise FileNotFoundError(source_path)

    if not os.getenv("OPENAI_API_KEY"):
        raise RuntimeError("OPENAI_API_KEY is missing. Put it in your environment or .env file.")

    print(f"Reading: {source_path}")
    raw_text = extract_text(source_path)

    if not raw_text.strip():
        raise RuntimeError("No text was extracted from the file.")

    print("Splitting document into trait blocks and extracting with agent...")

    try:
        result, metadata = extract_traits_with_agent(
            raw_text,
            model=args.model,
            max_batch_chars=args.max_batch_chars,
            max_workers=args.workers,
        )
    except ValidationError as exc:
        print("The agent returned JSON, but it failed local validation.")
        raise exc

    write_preview(result, metadata, output_path)

    print()
    print("Import preview created.")
    print(f"Trait blocks found:       {metadata['trait_blocks_found']}")
    print(f"Pre-validation invalid:   {metadata['pre_validation_invalid']}")
    print(f"Sent to agent:            {metadata['sent_to_agent']}")
    print(f"Batches:                  {metadata['batches']}")
    print(f"Thread workers:           {metadata['thread_workers']}")
    print(f"Duplicate traits skipped: {metadata['duplicate_count']}")
    print(f"Valid traits:             {len(result.valid_traits)}")
    print(f"Invalid traits:           {len(result.invalid_traits)}")
    print(f"Preview file:             {output_path}")

    if result.invalid_traits:
        print()
        print("Invalid traits found. Review the preview before committing.")

    if args.commit:
        database_url = os.getenv("DATABASE_URL")
        if not database_url:
            raise RuntimeError("DATABASE_URL is missing. Required when using --commit.")

        print()
        print("Committing valid traits to database...")
        inserted_ids = insert_valid_traits(result, database_url)
        print(f"Inserted {len(inserted_ids)} traits.")


if __name__ == "__main__":
    main()