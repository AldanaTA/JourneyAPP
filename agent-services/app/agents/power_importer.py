"""
power_importer.py

Supervised import agent for the Journey `powers` and `power_categories` Postgres tables.

Reusable utilities such as file text extraction, text normalization, JSON writing,
and character-count batching live in helpers.py so other import agents can share
them.
"""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Any, Literal

from dotenv import load_dotenv
from openai import OpenAI
from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    ValidationError,
    field_validator,
    model_validator,
)

from helpers.agent_helpers import (
    batch_by_char_count,
    extract_text,
    get_max_workers,
    normalize_text,
    run_threaded_batches,
    write_json_file,
)


# ----------------------------
# Your DB enums
# ----------------------------

PowerType = Literal["Destruction", "Support", "Sabotage", "Utility"]


class PowerCategory(str, Enum):
    damage = "damage"
    defense = "defense"
    healing_recovery = "healing_recovery"
    buff = "buff"
    debuff = "debuff"
    control = "control"
    mobility = "mobility"
    strong_attack = "strong_attack"
    light_attack = "light_attack"
    magic = "magic"
    summoning_companions = "summoning_companions"

    acid = "acid"
    bludgeoning = "bludgeoning"
    cold = "cold"
    dark = "dark"
    fire = "fire"
    force = "force"
    light = "light"
    lightning = "lightning"
    piercing = "piercing"
    poison = "poison"
    psychic = "psychic"
    slashing = "slashing"

    bleeding = "bleeding"
    blessed = "blessed"
    blinded = "blinded"
    charmed = "charmed"
    cursed = "cursed"
    dazed = "dazed"
    deafened = "deafened"
    enfeeble = "enfeeble"
    exhaustion = "exhaustion"
    frightened = "frightened"
    impaired = "impaired"
    incapacitated = "incapacitated"
    infatuated = "infatuated"
    inspired = "inspired"
    invisible = "invisible"
    mighty = "mighty"
    petrified = "petrified"
    prone = "prone"
    restrained = "restrained"
    rush = "rush"
    silenced = "silenced"
    sluggish = "sluggish"
    stunned = "stunned"
    suppressed = "suppressed"
    unconscious = "unconscious"
    vulnerable = "vulnerable"

    declared = "declared"
    triggered = "triggered"


POWER_CATEGORY_VALUES = [category.value for category in PowerCategory]
VALID_POWER_CATEGORIES = set(POWER_CATEGORY_VALUES)

TIMING_POWER_CATEGORIES = {
    PowerCategory.declared,
    PowerCategory.triggered,
}


# ----------------------------
# Pydantic validation models
# ----------------------------

class PowerRow(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1)
    lvl: int = Field(ge=1)
    type: PowerType

    tp_cost: int = Field(ge=0)
    hp_cost: int | None = Field(default=None, ge=0)
    mp_cost: int | None = Field(default=None, ge=0)
    ep_cost: int | None = Field(default=None, ge=0)

    material_components: str = "None"
    verbal: bool
    sight: bool
    somatic: bool
    is_distinct: bool
    concentration: bool

    range: str = "5"
    area: str = "0"
    duration: str = "Instantaneous"

    effect: str = Field(min_length=1)
    empower_effect: str | None = None
    lvl_up_effect: str | None = None

    categories: list[PowerCategory] = Field(default_factory=list)

    @field_validator(
        "name",
        "material_components",
        "range",
        "area",
        "duration",
        "effect",
        mode="before",
    )
    @classmethod
    def clean_required_strings(cls, value: Any) -> str:
        if value is None:
            return ""
        return str(value).strip()

    @field_validator("empower_effect", "lvl_up_effect", mode="before")
    @classmethod
    def clean_optional_strings(cls, value: Any) -> str | None:
        if value is None:
            return None

        text = str(value).strip()

        if text == "" or text.lower() in {"none", "n/a", "null", "-"}:
            return None

        return text

    @field_validator("material_components", mode="after")
    @classmethod
    def default_material_components(cls, value: str) -> str:
        if value.strip() in {"", "-"}:
            return "None"
        return value

    @field_validator("range", mode="after")
    @classmethod
    def default_range(cls, value: str) -> str:
        if value.strip() in {"", "None", "none", "-"}:
            return "5"
        return value

    @field_validator("area", mode="after")
    @classmethod
    def default_area(cls, value: str) -> str:
        if value.strip() in {"", "None", "none", "-"}:
            return "0"
        return value

    @field_validator("duration", mode="after")
    @classmethod
    def default_duration(cls, value: str) -> str:
        if value.strip() in {"", "None", "none", "-"}:
            return "Instantaneous"
        return value

    @field_validator("categories", mode="before")
    @classmethod
    def clean_categories(cls, value: Any) -> list[str]:
        if not value:
            return ["declared"]

        cleaned: list[str] = []

        for item in value:
            category = (
                str(item)
                .strip()
                .lower()
                .replace(" ", "_")
                .replace("-", "_")
            )

            if category in VALID_POWER_CATEGORIES and category not in cleaned:
                cleaned.append(category)

        return cleaned or ["declared"]

    @model_validator(mode="after")
    def apply_category_cleanup(self):
        self.categories = finalize_power_categories(self)
        return self


class InvalidPower(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source_text: str
    errors: list[str]


class AgentImportResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    valid_powers: list[PowerRow]
    invalid_powers: list[InvalidPower]


# ----------------------------
# Category cleanup / fallback
# ----------------------------

def unique_power_categories(categories: list[PowerCategory]) -> list[PowerCategory]:
    seen: set[PowerCategory] = set()
    result: list[PowerCategory] = []

    for category in categories:
        if category not in seen:
            seen.add(category)
            result.append(category)

    return result


def infer_power_timing_category(power: PowerRow) -> PowerCategory:
    """
    Returns exactly one timing category:
    - declared
    - triggered

    Most powers are declared because they are intentionally cast/activated.
    Triggered should only be used when the power happens because of a condition.
    """
    text = f"""
    {power.name}
    {power.effect}
    {power.empower_effect or ""}
    {power.lvl_up_effect or ""}
    """.lower()

    triggered_patterns = [
        r"\bwhen\b",
        r"\bwhenever\b",
        r"\bafter\b",
        r"\bbefore\b",
        r"\bat the start of\b",
        r"\bat the end of\b",
        r"\bon a hit\b",
        r"\bon a miss\b",
        r"\bon a success\b",
        r"\bon a failure\b",
        r"\bif you\b",
        r"\bif a creature\b",
        r"\bif an enemy\b",
        r"\bif an ally\b",
    ]

    if any(re.search(pattern, text) for pattern in triggered_patterns):
        return PowerCategory.triggered

    return PowerCategory.declared


def infer_fallback_power_categories(power: PowerRow) -> list[PowerCategory]:
    """
    Adds backup categories in case the agent gives too few categories.
    This does not replace the agent categories. It supplements them.
    """
    text = f"""
    {power.name}
    {power.type}
    {power.effect}
    {power.empower_effect or ""}
    {power.lvl_up_effect or ""}
    """.lower()

    categories: list[PowerCategory] = []

    if power.type == "Destruction":
        categories.append(PowerCategory.damage)
    elif power.type == "Support":
        categories.append(PowerCategory.buff)
    elif power.type == "Sabotage":
        categories.append(PowerCategory.debuff)
    elif power.type == "Utility":
        categories.append(PowerCategory.magic)

    damage_keywords: dict[PowerCategory, list[str]] = {
        PowerCategory.acid: ["acid"],
        PowerCategory.bludgeoning: ["bludgeoning"],
        PowerCategory.cold: ["cold", "ice", "frost"],
        PowerCategory.dark: ["dark", "shadow", "necrotic"],
        PowerCategory.fire: ["fire", "flame", "burn"],
        PowerCategory.force: ["force"],
        PowerCategory.light: ["light", "radiant"],
        PowerCategory.lightning: ["lightning", "electric", "shock"],
        PowerCategory.piercing: ["piercing"],
        PowerCategory.poison: ["poison", "toxic"],
        PowerCategory.psychic: ["psychic", "mind"],
        PowerCategory.slashing: ["slashing"],
    }

    for category, keywords in damage_keywords.items():
        if any(keyword in text for keyword in keywords):
            categories.append(category)

    status_keywords: dict[PowerCategory, list[str]] = {
        PowerCategory.bleeding: ["bleeding"],
        PowerCategory.blessed: ["blessed"],
        PowerCategory.blinded: ["blinded"],
        PowerCategory.charmed: ["charmed"],
        PowerCategory.cursed: ["cursed"],
        PowerCategory.dazed: ["dazed"],
        PowerCategory.deafened: ["deafened"],
        PowerCategory.enfeeble: ["enfeeble", "enfeebled"],
        PowerCategory.exhaustion: ["exhaustion"],
        PowerCategory.frightened: ["frightened"],
        PowerCategory.impaired: ["impaired"],
        PowerCategory.incapacitated: ["incapacitated"],
        PowerCategory.infatuated: ["infatuated"],
        PowerCategory.inspired: ["inspired"],
        PowerCategory.invisible: ["invisible"],
        PowerCategory.mighty: ["mighty"],
        PowerCategory.petrified: ["petrified"],
        PowerCategory.prone: ["prone"],
        PowerCategory.restrained: ["restrained"],
        PowerCategory.rush: ["rush"],
        PowerCategory.silenced: ["silenced"],
        PowerCategory.sluggish: ["sluggish"],
        PowerCategory.stunned: ["stunned"],
        PowerCategory.suppressed: ["suppressed"],
        PowerCategory.unconscious: ["unconscious"],
        PowerCategory.vulnerable: ["vulnerable", "weakened"],
    }

    for category, keywords in status_keywords.items():
        if any(keyword in text for keyword in keywords):
            categories.append(category)

    if "strong attack" in text:
        categories.append(PowerCategory.strong_attack)

    if "light attack" in text:
        categories.append(PowerCategory.light_attack)

    if any(word in text for word in ["move", "movement", "pull", "push", "teleport", "speed"]):
        categories.append(PowerCategory.mobility)

    if any(word in text for word in ["summon", "companion", "minion", "undead"]):
        categories.append(PowerCategory.summoning_companions)

    if any(word in text for word in ["heal", "healing", "restore", "recover"]):
        categories.append(PowerCategory.healing_recovery)

    if any(word in text for word in ["shield", "guard", "resistance", "armor", "thp", "temp hp"]):
        categories.append(PowerCategory.defense)

    if any(
        word in text
        for word in [
            "stun",
            "stunned",
            "prone",
            "restrain",
            "restrained",
            "pull",
            "push",
            "control",
            "unconscious",
            "silenced",
            "sluggish",
        ]
    ):
        categories.append(PowerCategory.control)

    if any(word in text for word in ["magic", "mp", "cast", "power"]):
        categories.append(PowerCategory.magic)

    return unique_power_categories(categories)


def finalize_power_categories(power: PowerRow) -> list[PowerCategory]:
    """
    Guarantees:
    - At least 4 categories total.
    - Exactly one timing category.
    - Timing category is either declared or triggered.
    """
    categories = unique_power_categories(list(power.categories))

    non_timing_categories = [
        category
        for category in categories
        if category not in TIMING_POWER_CATEGORIES
    ]

    fallback_categories = infer_fallback_power_categories(power)

    for category in fallback_categories:
        if category not in non_timing_categories:
            non_timing_categories.append(category)

    # Need at least 3 non-timing categories + 1 timing category.
    generic_fillers = [
        PowerCategory.magic,
        PowerCategory.damage,
        PowerCategory.buff,
        PowerCategory.debuff,
        PowerCategory.control,
        PowerCategory.mobility,
    ]

    for category in generic_fillers:
        if len(non_timing_categories) >= 3:
            break

        if category not in non_timing_categories:
            non_timing_categories.append(category)

    timing_category = infer_power_timing_category(power)

    return unique_power_categories([
        *non_timing_categories[:5],
        timing_category,
    ])


# ----------------------------
# Power block splitting
# ----------------------------

@dataclass(frozen=True)
class PowerBlock:
    index: int
    name: str
    text: str


IGNORED_HEADER_LINES = {
    "powers",
    "casting powers",
    "empowering powers",
    "destructive powers",
    "destruction powers",
    "support powers",
    "sabotage powers",
    "utility powers",
    "ep powers",
    "mp powers",
}


REQUIRED_POWER_LABELS = [
    "LVL:",
    "Type:",
    "Cost:",
    "Material Components:",
    "Components:",
    "Range:",
    "Area:",
    "Duration:",
    "Effect:",
]


def is_possible_power_name(line: str) -> bool:
    cleaned = line.strip()

    if not cleaned:
        return False

    if ":" in cleaned:
        return False

    if cleaned.lower() in IGNORED_HEADER_LINES:
        return False

    if len(cleaned) > 80:
        return False

    if not re.search(r"[A-Za-z]", cleaned):
        return False

    return True


def has_required_power_labels(block_text: str) -> bool:
    return all(label in block_text for label in REQUIRED_POWER_LABELS)


def split_power_blocks(raw_text: str) -> list[PowerBlock]:
    """
    Finds power blocks by looking for:

        Some Power Name
        LVL: ...

    Then captures until the next line that looks like another power name
    followed by LVL:.
    """
    text = normalize_text(raw_text)
    lines = [line.rstrip() for line in text.split("\n")]

    start_indexes: list[int] = []

    for i in range(len(lines) - 1):
        current_line = lines[i].strip()
        next_line = lines[i + 1].strip()

        if is_possible_power_name(current_line) and next_line.startswith("LVL:"):
            start_indexes.append(i)

    blocks: list[PowerBlock] = []

    for block_number, start in enumerate(start_indexes, start=1):
        end = start_indexes[block_number] if block_number < len(start_indexes) else len(lines)
        block_lines = lines[start:end]
        block_text = "\n".join(block_lines).strip()
        name = block_lines[0].strip()

        if has_required_power_labels(block_text):
            blocks.append(PowerBlock(index=block_number, name=name, text=block_text))

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


def prevalidate_power_block(block: PowerBlock) -> list[str]:
    """
    Catches cases that your DB schema cannot represent safely.

    We only reject things that would clearly break NOT NULL / INT columns
    or cause major data loss.
    """
    errors: list[str] = []

    lvl_raw = get_label_value(block.text, "LVL:")
    cost_raw = get_label_value(block.text, "Cost:")
    type_raw = get_label_value(block.text, "Type:")

    if not lvl_raw:
        errors.append("Missing LVL.")
    elif not re.fullmatch(r"\d+", lvl_raw):
        errors.append(f"LVL must be an integer, got: {lvl_raw!r}.")

    if not type_raw:
        errors.append("Missing Type.")
    elif type_raw.strip() not in {"Destruction", "Support", "Sabotage", "Utility"}:
        errors.append(f"Invalid Type: {type_raw!r}.")

    if not cost_raw:
        errors.append("Missing Cost.")
    else:
        if "*" in cost_raw:
            errors.append(
                f"Variable cost cannot fit mp_cost/hp_cost/ep_cost integer columns: {cost_raw!r}."
            )

        if not re.search(r"(?i)\b\d+\s*TP\b", cost_raw):
            errors.append(f"Missing numeric TP cost in Cost: {cost_raw!r}.")

    return errors


def remove_invalid_blocks(blocks: list[PowerBlock]) -> tuple[list[PowerBlock], list[InvalidPower]]:
    valid_for_agent: list[PowerBlock] = []
    invalid_powers: list[InvalidPower] = []

    for block in blocks:
        errors = prevalidate_power_block(block)

        if errors:
            invalid_powers.append(
                InvalidPower(
                    source_text=block.text,
                    errors=errors,
                )
            )
        else:
            valid_for_agent.append(block)

    return valid_for_agent, invalid_powers


# ----------------------------
# Structured output schema
# ----------------------------

POWER_IMPORT_JSON_SCHEMA: dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "valid_powers": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "name": {"type": "string"},
                    "lvl": {"type": "integer"},
                    "type": {
                        "type": "string",
                        "enum": ["Destruction", "Support", "Sabotage", "Utility"],
                    },
                    "tp_cost": {"type": "integer"},
                    "hp_cost": {"type": ["integer", "null"]},
                    "mp_cost": {"type": ["integer", "null"]},
                    "ep_cost": {"type": ["integer", "null"]},
                    "material_components": {"type": "string"},
                    "verbal": {"type": "boolean"},
                    "sight": {"type": "boolean"},
                    "somatic": {"type": "boolean"},
                    "is_distinct": {"type": "boolean"},
                    "concentration": {"type": "boolean"},
                    "range": {"type": "string"},
                    "area": {"type": "string"},
                    "duration": {"type": "string"},
                    "effect": {"type": "string"},
                    "empower_effect": {"type": ["string", "null"]},
                    "lvl_up_effect": {"type": ["string", "null"]},
                    "categories": {
                        "type": "array",
                        "items": {
                            "type": "string",
                            "enum": POWER_CATEGORY_VALUES,
                        },
                    },
                },
                "required": [
                    "name",
                    "lvl",
                    "type",
                    "tp_cost",
                    "hp_cost",
                    "mp_cost",
                    "ep_cost",
                    "material_components",
                    "verbal",
                    "sight",
                    "somatic",
                    "is_distinct",
                    "concentration",
                    "range",
                    "area",
                    "duration",
                    "effect",
                    "empower_effect",
                    "lvl_up_effect",
                    "categories",
                ],
            },
        },
        "invalid_powers": {
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
    "required": ["valid_powers", "invalid_powers"],
}


CATEGORY_RULES = f"""
Category assignment rules:

Every valid power must have at least 2 categories total but can have up to 6.
Exactly one category must be either declared or triggered.

Timing categories:
- declared: the power is intentionally cast, activated, or chosen by the user.
- triggered: the power happens because a condition occurs, such as when hit, on success, on failure, at the start of a round, or at the end of a round.
- Most normal powers should be declared.

General categories:
- damage: deals, increases, converts, stores, or modifies damage.
- defense: protects, shields, reduces damage, grants THP, improves survivability, or grants resistance.
- healing_recovery: heals, restores, regenerates, or recovers resources/HP.
- buff: improves allies, stats, rolls, movement, defenses, damage, or effectiveness.
- debuff: worsens enemies, rolls, stats, defenses, damage, movement, or effectiveness.
- control: restricts actions, movement, position, choices, reactions, or applies disabling conditions.
- mobility: improves, reduces, forces, or modifies movement or positioning.
- strong_attack: is based on or modifies a strong attack.
- light_attack: is based on or modifies a light attack.
- magic: uses magic, casting, powers, MP, spell-like effects, arcane/psychic/divine/cursed effects.
- summoning_companions: creates, controls, commands, buffs, or interacts with summons, companions, undead, pets, or minions.

Damage type categories:
- acid
- bludgeoning
- cold
- dark
- fire
- force
- light
- lightning
- piercing
- poison
- psychic
- slashing

Status ailment categories:
- bleeding
- blessed
- blinded
- charmed
- cursed
- dazed
- deafened
- enfeeble
- exhaustion
- frightened
- impaired
- incapacitated
- infatuated
- inspired
- invisible
- mighty
- petrified
- prone
- restrained
- rush
- silenced
- sluggish
- stunned
- suppressed
- unconscious
- vulnerable

Important:
- Use at least 1 or more non-timing categories based on the power's effect, damage type, status ailment, power type, or role.
- Use only these enum values:
{", ".join(POWER_CATEGORY_VALUES)}
"""


AGENT_INSTRUCTIONS = f"""
You are a supervised data import agent for a tabletop power database.

You will receive pre-split power blocks. Each block should represent exactly one power.
Do not extract intro/rules text, category headers, or section headers.

Return only JSON matching the provided schema.

Database columns:
- name TEXT NOT NULL
- lvl INT NOT NULL
- type enum: Destruction, Support, Sabotage, Utility
- tp_cost INT NOT NULL
- hp_cost INT nullable
- mp_cost INT nullable
- ep_cost INT nullable
- material_components TEXT DEFAULT 'None'
- verbal BOOLEAN NOT NULL
- sight BOOLEAN NOT NULL
- somatic BOOLEAN NOT NULL
- is_distinct BOOLEAN NOT NULL
- concentration BOOLEAN NOT NULL
- range TEXT DEFAULT '5'
- area TEXT DEFAULT '0'
- duration TEXT DEFAULT 'Instantaneous'
- effect TEXT NOT NULL
- empower_effect TEXT nullable
- lvl_up_effect TEXT nullable
- categories power_category[] equivalent in JSON

Cost parsing rules:
- Parse "Cost:" into tp_cost, hp_cost, mp_cost, and ep_cost.
- Example: "Cost: 20EP 15HP 4TP" means ep_cost=20, hp_cost=15, tp_cost=4, mp_cost=null.
- Example: "Cost: 55MP 3TP" means mp_cost=55, tp_cost=3, hp_cost=null, ep_cost=null.
- If a resource does not appear, use null for that resource.
- TP must be a number.
- If Cost contains an alternate cast time, such as "12MP 4TP or 1 minute to cast", parse the numeric costs normally and add this sentence to the beginning of effect: "Alternate casting: 1 minute to cast."
- If a block has variable cost like "*MP", place it in invalid_powers.

Component parsing rules:
The source field "Components:" maps to database booleans:
- V means verbal = true
- O means sight = true
- S means somatic = true
- D means is_distinct = true
- C means concentration = true
- None means all five booleans are false
- Missing letters are false
- Ignore commas and whitespace

Field mapping rules:
- "Material Components:" maps to material_components.
- Empty material components, "-", or "None" should become "None".
- "Range: None" should become "5" because the DB default is "5".
- "Area: None" should become "0" because the DB default is "0".
- "Duration: None" should become "Instantaneous" because the DB default is "Instantaneous".
- "Empower:" maps to empower_effect. If it says "None", use "None".
- "LVL UP:" maps to lvl_up_effect. If it says "None", use "None".
- Some source blocks mistakenly use a final "LVL:" line where they clearly mean "LVL UP:". If a second LVL-like field appears near the end after Empower, treat it as lvl_up_effect.
- Preserve the Effect/Empower/LVL UP wording as much as possible.

{CATEGORY_RULES}

Invalid rules:
Put a block into invalid_powers when:
- name is missing
- lvl is missing, "None", or not an integer
- type is not one of Destruction, Support, Sabotage, Utility
- Cost is missing
- Cost has variable resource values like "*MP"
- tp_cost cannot be parsed
- effect is missing
"""


# ----------------------------
# Agent batching
# ----------------------------

def make_block_batch_text(blocks: list[PowerBlock]) -> str:
    parts: list[str] = []

    for block in blocks:
        parts.append(f"--- POWER BLOCK {block.index}: {block.name} ---\n{block.text}")

    return "\n\n".join(parts)


def extract_powers_batch_with_agent(blocks: list[PowerBlock], model: str) -> AgentImportResult:
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
                            "Extract the following pre-split power blocks into database rows.\n\n"
                            f"{make_block_batch_text(blocks)}"
                        ),
                    }
                ],
            }
        ],
        text={
            "format": {
                "type": "json_schema",
                "name": "power_import_result",
                "strict": True,
                "schema": POWER_IMPORT_JSON_SCHEMA,
            }
        },
    )

    raw_json = response.output_text
    parsed = json.loads(raw_json)

    return AgentImportResult.model_validate(parsed)


def merge_results(results: list[AgentImportResult]) -> AgentImportResult:
    valid_powers: list[PowerRow] = []
    invalid_powers: list[InvalidPower] = []

    for result in results:
        valid_powers.extend(result.valid_powers)
        invalid_powers.extend(result.invalid_powers)

    return AgentImportResult(
        valid_powers=valid_powers,
        invalid_powers=invalid_powers,
    )


def dedupe_powers(result: AgentImportResult) -> tuple[AgentImportResult, int]:
    """
    Since powers are globally unique by normalized name_key,
    skip duplicate names before committing.
    """
    seen: set[str] = set()
    unique_powers: list[PowerRow] = []
    duplicate_count = 0

    for power in result.valid_powers:
        key = power.name.strip().lower()

        if key in seen:
            duplicate_count += 1
            continue

        seen.add(key)
        unique_powers.append(power)

    return (
        AgentImportResult(
            valid_powers=unique_powers,
            invalid_powers=result.invalid_powers,
        ),
        duplicate_count,
    )


def extract_powers_with_agent(
    raw_text: str,
    model: str,
    max_batch_chars: int,
    max_workers: int = get_max_workers(),
) -> tuple[AgentImportResult, dict[str, Any]]:
    all_blocks = split_power_blocks(raw_text)
    blocks_for_agent, pre_invalid = remove_invalid_blocks(all_blocks)

    metadata: dict[str, Any] = {
        "power_blocks_found": len(all_blocks),
        "pre_validation_invalid": len(pre_invalid),
        "sent_to_agent": len(blocks_for_agent),
        "batches": 0,
        "thread_workers": 0,
        "duplicate_count": 0,
    }

    if not all_blocks:
        return (
            AgentImportResult(
                valid_powers=[],
                invalid_powers=[
                    InvalidPower(
                        source_text=raw_text[:4000],
                        errors=[
                            "No power blocks found. Expected a power name line followed by 'LVL:'."
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

    batch_results: list[AgentImportResult] = []

    if pre_invalid:
        batch_results.append(
            AgentImportResult(
                valid_powers=[],
                invalid_powers=pre_invalid,
            )
        )

    threaded_results = run_threaded_batches(
        batches,
        worker=lambda batch: extract_powers_batch_with_agent(batch, model=model),
        max_workers=max_workers,
    )

    batch_results.extend(threaded_results)

    merged_result = merge_results(batch_results)
    deduped_result, duplicate_count = dedupe_powers(merged_result)

    metadata["duplicate_count"] = duplicate_count

    return deduped_result, metadata


# ----------------------------
# Import preview output
# ----------------------------

def write_preview(
    result: AgentImportResult,
    metadata: dict[str, Any],
    output_path: Path,
) -> None:
    preview = {
        "metadata": metadata,
        "valid_powers": [
            power.model_dump(mode="json")
            for power in result.valid_powers
        ],
        "invalid_powers": [
            power.model_dump(mode="json")
            for power in result.invalid_powers
        ],
    }

    write_json_file(preview, output_path)


# ----------------------------
# Database upsert
# ----------------------------

UPSERT_POWER_SQL = """
INSERT INTO powers (
    source_id,
    name,
    lvl,
    type,
    tp_cost,
    hp_cost,
    mp_cost,
    ep_cost,
    material_components,
    verbal,
    sight,
    somatic,
    is_distinct,
    concentration,
    range,
    area,
    duration,
    effect,
    empower_effect,
    lvl_up_effect
)
VALUES (
    %(source_id)s,
    %(name)s,
    %(lvl)s,
    %(type)s,
    %(tp_cost)s,
    %(hp_cost)s,
    %(mp_cost)s,
    %(ep_cost)s,
    %(material_components)s,
    %(verbal)s,
    %(sight)s,
    %(somatic)s,
    %(is_distinct)s,
    %(concentration)s,
    %(range)s,
    %(area)s,
    %(duration)s,
    %(effect)s,
    %(empower_effect)s,
    %(lvl_up_effect)s
)
ON CONFLICT (name_key)
DO UPDATE SET
    name = EXCLUDED.name,
    lvl = EXCLUDED.lvl,
    type = EXCLUDED.type,
    tp_cost = EXCLUDED.tp_cost,
    hp_cost = EXCLUDED.hp_cost,
    mp_cost = EXCLUDED.mp_cost,
    ep_cost = EXCLUDED.ep_cost,
    material_components = EXCLUDED.material_components,
    verbal = EXCLUDED.verbal,
    sight = EXCLUDED.sight,
    somatic = EXCLUDED.somatic,
    is_distinct = EXCLUDED.is_distinct,
    concentration = EXCLUDED.concentration,
    range = EXCLUDED.range,
    area = EXCLUDED.area,
    duration = EXCLUDED.duration,
    effect = EXCLUDED.effect,
    empower_effect = EXCLUDED.empower_effect,
    lvl_up_effect = EXCLUDED.lvl_up_effect,
    updated_at = NOW()
RETURNING id;
"""


DELETE_POWER_CATEGORIES_SQL = """
DELETE FROM power_categories
WHERE power_id = %(power_id)s;
"""


INSERT_POWER_CATEGORY_SQL = """
INSERT INTO power_categories (
    power_id,
    category
)
VALUES (
    %(power_id)s,
    %(category)s::power_category
)
ON CONFLICT (power_id, category)
DO NOTHING;
"""


def normalize_database_url(database_url: str) -> str:
    return (
        database_url
        .replace("postgresql+asyncpg://", "postgresql://", 1)
        .replace("postgresql+psycopg://", "postgresql://", 1)
    )


def insert_valid_powers(result: AgentImportResult, database_url: str, source_id: str) -> list[str]:
    try:
        import psycopg
    except ImportError as exc:
        raise RuntimeError("Missing dependency. Install with: pip install psycopg[binary]") from exc

    upserted_ids: list[str] = []

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        with conn.cursor() as cur:
            for power in result.valid_powers:
                power_data = power.model_dump(mode="json")
                categories = power_data.pop("categories", [])

                cur.execute(UPSERT_POWER_SQL, {"source_id": source_id, **power_data})
                power_id = cur.fetchone()[0]

                # Replace old categories with the current import result.
                cur.execute(
                    DELETE_POWER_CATEGORIES_SQL,
                    {"power_id": power_id},
                )

                for category in categories:
                    cur.execute(
                        INSERT_POWER_CATEGORY_SQL,
                        {
                            "power_id": power_id,
                            "category": category,
                        },
                    )

                upserted_ids.append(str(power_id))

        conn.commit()

    return upserted_ids