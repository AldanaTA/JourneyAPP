"""
creature_importer.py

Import parser for the Journey `creatures` table and its child tables (`creature_augments`,
`creature_abilities`, `creature_attacks`, `creature_attack_properties`).

Like the item importer this one does not call a model: stat blocks follow the template in the
Storyteller Tools (Creature Creation), so they are parsed deterministically.

Flow:
- Split the text into stat blocks at each "Name:" line (blank templates are skipped)
- Read the header, lore, combat stats, skills, augments, and abilities
- In Declared, lines with "Damage:" are attacks and "casts <Power> at LVL n" lines are power casts
- Link abilities to game traits/powers by exact name; warn "did you mean ...?" on near misses
- Reject unknown creature types (types are per source), augment targets (damage types or status
  conditions), attack damage types, and weapon properties
- Return valid_creatures and invalid_creatures
- Insert only valid creatures using synchronous psycopg
"""

from __future__ import annotations

import difflib
import re
from dataclasses import dataclass, field
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

from agents.item_importer import DAMAGE_PATTERN, key
from agents.race_importer import parse_size_range
from agents.trait_importer import normalize_database_url
from helpers.agent_helpers import extract_text, normalize_text

__all__ = [
    "AgentCreatureImportResult",
    "extract_creatures",
    "extract_creatures_from_archive",
    "extract_text",
    "insert_valid_creatures",
]


RaceSize = Literal["T", "S", "M", "L", "H", "C"]
AbilityTiming = Literal["passive", "triggered", "declared"]
AugmentKind = Literal["immune", "resistant", "weak"]

MISSING_TEXT_VALUES = {"", "-", "none", "n/a", "null"}
# How close a name must be to count as a likely typo ("did you mean ...?").
TYPO_CUTOFF = 0.8


# ----------------------------
# Pydantic models
# ----------------------------

class CreatureAugment(BaseModel):
    model_config = ConfigDict(extra="forbid")

    kind: AugmentKind
    # Canonical name of the damage type or status condition.
    target: str
    # Exactly one of these is set.
    damage_type: str | None = None
    status_condition: str | None = None


class CreatureAbility(BaseModel):
    model_config = ConfigDict(extra="forbid")

    timing: AbilityTiming
    name: str
    effect: str
    trait: str | None = None
    power: str | None = None
    lvl: int | None = Field(default=None, ge=1)
    uses: int | None = Field(default=None, ge=0)
    mp_cost: int | None = Field(default=None, ge=0)
    ep_cost: int | None = Field(default=None, ge=0)
    tp_cost: int | None = Field(default=None, ge=0)


class CreatureAttack(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str
    skill: str
    range_ft: int | None = None
    damage_dice: str
    damage_bonus: str | None = None
    damage_type: str
    mag: int | None = None
    properties: list[str] = Field(default_factory=list)


class CreatureRow(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1)
    creature_type: str
    threat_lvl: int = Field(ge=1)
    min_size: RaceSize
    max_size: RaceSize | None = None
    description: str | None = None
    languages: str | None = None
    lore: str | None = None
    hp: int = Field(ge=0)
    wp: int | None = Field(default=None, ge=0)
    mp: int = Field(default=0, ge=0)
    ep: int = Field(default=0, ge=0)
    prio: int = 0
    movement: int = Field(default=30, ge=0)
    pd: int = 0
    md: int = 0
    skills: list[dict[str, Any]] = Field(default_factory=list)
    augments: list[CreatureAugment] = Field(default_factory=list)
    abilities: list[CreatureAbility] = Field(default_factory=list)
    attacks: list[CreatureAttack] = Field(default_factory=list)
    # Non-blocking notes for the preview (typo suggestions, defaults applied, ...).
    warnings: list[str] = Field(default_factory=list)


class InvalidCreature(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source_text: str
    errors: list[str]


class AgentCreatureImportResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    valid_creatures: list[CreatureRow]
    invalid_creatures: list[InvalidCreature]


# ----------------------------
# Reference data
# ----------------------------

@dataclass
class ReferenceData:
    # All map name_key -> display name.
    creature_types: dict[str, str]      # for the import's source only
    traits: dict[str, str]
    powers: dict[str, str]
    damage_types: dict[str, str]
    status_conditions: dict[str, str]
    weapon_properties: dict[str, str]


def fetch_reference_data(database_url: str, source_id: str) -> ReferenceData:
    import psycopg

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        with conn.cursor() as cur:
            def names(sql: str, params: tuple[Any, ...] = ()) -> dict[str, str]:
                cur.execute(sql, params)
                return {key(row[0]): row[0] for row in cur.fetchall()}

            return ReferenceData(
                creature_types=names("SELECT name FROM creature_types WHERE source_id = %s;", (source_id,)),
                traits=names("SELECT trait_name FROM traits;"),
                powers=names("SELECT name FROM powers;"),
                damage_types=names("SELECT name FROM damage_types;"),
                status_conditions=names("SELECT name FROM status_conditions;"),
                weapon_properties=names("SELECT name FROM weapon_properties;"),
            )


def closest(name: str, options: dict[str, str]) -> str | None:
    """Display name of the closest option when it is a likely typo of `name`, else None."""
    matches = difflib.get_close_matches(key(name), list(options), n=1, cutoff=TYPO_CUTOFF)
    return options[matches[0]] if matches else None


def did_you_mean(name: str, options: dict[str, str]) -> str:
    suggestion = closest(name, options)
    return f" Did you mean {suggestion!r}?" if suggestion else ""


# ----------------------------
# Stat block splitting and parsing
# ----------------------------

NAME_LINE = re.compile(r"(?i)^name\s*:\s*(.*)$")

# Section headings ("Lore", "Combat", "Passives:", ...) on a line of their own.
SECTION_HEADING = re.compile(r"(?i)^(lore|combat|skills|augments|abilities|passives|triggers|declared)\s*:?\s*$")
TIMING_SECTIONS = {"passives": "passive", "triggers": "triggered", "declared": "declared"}

HEADER_LABELS = re.compile(r"(?i)\b(name|threat\s*lvl|type|size|description|languages)\s*:")
COMBAT_LABELS = re.compile(r"(?i)\b(hp|wp|mp|ep|prio|mvm|mv|pd|md)\s*:")
SKILLS_LINE = re.compile(r"(?i)^skills\s*:\s*(.*)$")
AUGMENT_LINE = re.compile(r"(?i)^(immune|resistant|weak)\s*:\s*(.*)$")
COST_LINE = re.compile(r"(?i)^cost\s*:\s*(.*)$")
USES_LINE = re.compile(r"(?i)^uses\s*:\s*(.*)$")
# "Ability Name: effect" — a short name (at most 8 words) before the first colon.
ABILITY_START = re.compile(r"^([A-Za-z][^:]{0,60}?)\s*:\s*(.+)$")
POWER_CAST = re.compile(r"(?i)\bcasts\s+(.+?)\s+at\s+lvl\s*(\d+)")
RESOURCE_COST = re.compile(r"(?i)(\d+)\s*(MP|EP|TP)\b")


@dataclass
class CreatureBlock:
    text: str
    header: dict[str, str] = field(default_factory=dict)
    combat: dict[str, str] = field(default_factory=dict)
    lore: list[str] = field(default_factory=list)
    skills_text: list[str] = field(default_factory=list)
    augments: list[tuple[str, str]] = field(default_factory=list)
    # (timing, lines) per ability-ish entry, in order.
    entries: list[tuple[str, list[str]]] = field(default_factory=list)
    stray_lines: list[str] = field(default_factory=list)


def split_creature_blocks(raw_text: str) -> list[list[str]]:
    """Each stat block starts at a "Name:" line and runs to the next one."""
    lines = [line.strip() for line in normalize_text(raw_text).split("\n")]
    starts = [i for i, line in enumerate(lines) if NAME_LINE.match(line)]
    return [lines[start:(starts[n + 1] if n + 1 < len(starts) else len(lines))] for n, start in enumerate(starts)]


def parse_inline_labels(line: str, pattern: re.Pattern[str]) -> dict[str, str]:
    """'Threat LVL: 7 Type: Humanoid  Size: S-M' -> {'threat lvl': '7', 'type': 'Humanoid', 'size': 'S-M'}."""
    matches = list(pattern.finditer(line))
    values: dict[str, str] = {}
    for n, match in enumerate(matches):
        end = matches[n + 1].start() if n + 1 < len(matches) else len(line)
        values[re.sub(r"\s+", " ", match.group(1).lower())] = line[match.end():end].strip()
    return values


def read_block(lines: list[str]) -> CreatureBlock:
    block = CreatureBlock(text="\n".join(line for line in lines if line))
    section = "header"
    timing: str | None = None
    last_header_label: str | None = None

    for line in lines:
        if not line:
            continue

        heading = SECTION_HEADING.match(line)
        if heading:
            name = heading.group(1).lower()
            if name in TIMING_SECTIONS:
                section, timing = "abilities", TIMING_SECTIONS[name]
            else:
                section, timing = name, None
            continue

        skills = SKILLS_LINE.match(line)
        if skills:
            block.skills_text.append(skills.group(1))
            section = "skills"
            continue

        if section == "header":
            labels = parse_inline_labels(line, HEADER_LABELS)
            if labels:
                block.header.update(labels)
                last_header_label = list(labels)[-1]
            elif last_header_label == "description":
                # A description that wraps onto the next line.
                block.header["description"] = f"{block.header['description']} {line}".strip()
            else:
                block.stray_lines.append(line)

        elif section == "lore":
            block.lore.append(line)

        elif section == "combat":
            labels = parse_inline_labels(line, COMBAT_LABELS)
            if labels:
                block.combat.update(labels)
            else:
                block.stray_lines.append(line)

        elif section == "skills":
            block.skills_text.append(line)

        elif section == "augments":
            augment = AUGMENT_LINE.match(line)
            if augment:
                block.augments.append((augment.group(1).lower(), augment.group(2)))
            else:
                block.stray_lines.append(line)

        elif section == "abilities" and timing:
            is_label = COST_LINE.match(line) or USES_LINE.match(line)
            starts_entry = ABILITY_START.match(line) and len(ABILITY_START.match(line).group(1).split()) <= 8
            if starts_entry and not is_label:
                block.entries.append((timing, [line]))
            elif block.entries:
                # Cost/Uses lines and wrapped effect text belong to the previous entry.
                block.entries[-1][1].append(line)
            else:
                block.stray_lines.append(line)

        else:
            # Text under "Abilities" before Passives/Triggers/Declared, or anything unexpected.
            block.stray_lines.append(line)

    return block


def text_or_none(value: str | None) -> str | None:
    if value is None:
        return None
    cleaned = value.replace("\x00", "").strip()
    return None if cleaned.lower() in MISSING_TEXT_VALUES else cleaned


def to_int(value: str | None) -> int | None:
    """'125' -> 125, '30ft' -> 30, '-'/'' -> None. Non-numbers return None."""
    text = text_or_none(value)
    if text is None:
        return None
    match = re.fullmatch(r"(-?\d+)\s*(?:ft\.?|feet|%)?\.?", text, re.IGNORECASE)
    return int(match.group(1)) if match else None


def parse_skills(parts: list[str], errors: list[str]) -> list[dict[str, Any]]:
    """Accepts both 'Brawl 140%' and '140% Brawl'."""
    skills: list[dict[str, Any]] = []
    for raw in ", ".join(parts).split(","):
        item = raw.strip().rstrip(".")
        if not item:
            continue
        match = re.fullmatch(r"(.+?)\s+(\d+)\s*%", item) or re.fullmatch(r"(\d+)\s*%\s+(.+)", item)
        if not match:
            errors.append(f"Skill {item!r} must look like 'Brawl 140%'.")
            continue
        name, rating = (match.group(1), match.group(2)) if not match.group(1).isdigit() else (match.group(2), match.group(1))
        skills.append({"skill": name.strip(), "rating": int(rating)})
    return skills


def parse_costs(text: str) -> dict[str, int]:
    """'31MP 5TP' -> {'mp_cost': 31, 'tp_cost': 5}."""
    return {f"{unit.lower()}_cost": int(amount) for amount, unit in RESOURCE_COST.findall(text)}


def parse_attack(lines: list[str], refs: ReferenceData, errors: list[str]) -> CreatureAttack | None:
    """'Handgun: Firearm, Ammunition, Light, Range: 80ft, Damage: 1d6 + 7 piercing, Mag: 5'."""
    line = " ".join(lines)
    name, _, rest = line.partition(":")
    # Tolerate a missing comma before a label ("Thrown Range: 5ft" -> "Thrown, Range: 5ft").
    rest = re.sub(r"(?i)(?<=[^,\s])\s+(?=(range|damage|mag)\s*:)", ", ", rest)
    tokens = [token.strip() for token in rest.split(",") if token.strip()]
    if not tokens:
        errors.append(f"Attack {name.strip()!r} has no skill or damage.")
        return None

    skill = tokens[0]
    range_ft = mag = None
    damage = None
    properties: list[str] = []

    for token in tokens[1:]:
        label, sep, value = token.partition(":")
        label_key = label.strip().lower()
        if sep and label_key == "range":
            range_ft = to_int(value)
            if range_ft is None:
                errors.append(f"Attack {name.strip()!r}: range {value.strip()!r} must look like '5ft'.")
        elif sep and label_key == "damage":
            damage = value.strip()
        elif sep and label_key == "mag":
            mag = to_int(value)
            if mag is None:
                errors.append(f"Attack {name.strip()!r}: mag {value.strip()!r} must be a whole number.")
        else:
            prop = refs.weapon_properties.get(key(token))
            if prop is None:
                errors.append(
                    f"Attack {name.strip()!r}: unknown weapon property {token!r}."
                    + did_you_mean(token, refs.weapon_properties)
                )
            elif prop not in properties:
                properties.append(prop)

    match = DAMAGE_PATTERN.match(damage or "")
    if damage is None:
        errors.append(f"Attack {name.strip()!r} has no Damage.")
        return None
    if not match:
        errors.append(f"Attack {name.strip()!r}: damage must look like '1d8 + PD slashing', got {damage!r}.")
        return None

    damage_type = refs.damage_types.get(key(match.group(3)))
    if damage_type is None:
        errors.append(
            f"Attack {name.strip()!r}: unknown damage type {match.group(3)!r}."
            + did_you_mean(match.group(3), refs.damage_types)
        )
        return None

    return CreatureAttack(
        name=name.strip(),
        skill=skill,
        range_ft=range_ft,
        damage_dice=match.group(1).lower(),
        damage_bonus=match.group(2),
        damage_type=damage_type,
        mag=mag,
        properties=properties,
    )


def parse_ability(
    timing: str,
    lines: list[str],
    threat_lvl: int,
    refs: ReferenceData,
    warnings: list[str],
) -> CreatureAbility:
    first = ABILITY_START.match(lines[0])
    name = first.group(1).strip()
    effect_parts = [first.group(2).strip()]
    costs: dict[str, int] = {}
    uses: int | None = None

    for line in lines[1:]:
        cost = COST_LINE.match(line)
        uses_line = USES_LINE.match(line)
        if cost:
            costs.update(parse_costs(cost.group(1)))
        elif uses_line:
            uses = to_int(uses_line.group(1))
        else:
            effect_parts.append(line)

    effect = " ".join(effect_parts).strip()
    trait = power = None
    lvl = None

    cast = POWER_CAST.search(effect)
    if cast:
        # "The creature casts Darkness at LVL 7. 31MP 5TP"
        power_name = cast.group(1).strip()
        lvl = int(cast.group(2))
        costs = {**parse_costs(effect[cast.end():]), **costs}
        power = refs.powers.get(key(power_name))
        if power is None:
            warnings.append(
                f"{name!r} casts {power_name!r}, which is not in the power list; stored without a link."
                + did_you_mean(power_name, refs.powers)
            )
    elif key(name) in refs.traits:
        trait = refs.traits[key(name)]
        # Creation rules: a trait from the trait list has half the creature's threat LVL (min 1).
        lvl = max(1, threat_lvl // 2)
    elif key(name) in refs.powers:
        power = refs.powers[key(name)]
    else:
        suggestion = closest(name, {**refs.traits, **refs.powers})
        if suggestion:
            warnings.append(
                f"Ability {name!r} is not a game trait or power, so it is stored as the creature's own "
                f"ability. Did you mean {suggestion!r}?"
            )

    return CreatureAbility(
        timing=timing,
        name=name,
        effect=effect,
        trait=trait,
        power=power,
        lvl=lvl,
        uses=uses,
        mp_cost=costs.get("mp_cost"),
        ep_cost=costs.get("ep_cost"),
        tp_cost=costs.get("tp_cost"),
    )


def is_template(block: CreatureBlock) -> bool:
    """Blank or example stat blocks (like the one in the rules) have no numeric threat LVL or HP."""
    return to_int(block.header.get("threat lvl")) is None and to_int(block.combat.get("hp")) is None


def build_creature(block: CreatureBlock, refs: ReferenceData) -> CreatureRow | InvalidCreature:
    errors: list[str] = []
    warnings: list[str] = []
    header, combat = block.header, block.combat

    name = text_or_none(header.get("name"))
    if name is None:
        errors.append("Missing Name.")

    threat_lvl = to_int(header.get("threat lvl"))
    if threat_lvl is None or threat_lvl < 1:
        errors.append(f"Threat LVL must be a whole number of at least 1, got {header.get('threat lvl')!r}.")

    type_raw = text_or_none(header.get("type"))
    creature_type = refs.creature_types.get(key(type_raw or ""))
    if type_raw is None:
        errors.append("Missing Type.")
    elif creature_type is None:
        known = ", ".join(sorted(refs.creature_types.values())) or "none"
        errors.append(
            f"Unknown creature type {type_raw!r} for this source." + did_you_mean(type_raw, refs.creature_types)
            + f" Known types: {known}."
        )

    min_size, max_size, size_warnings = parse_size_range(header.get("size"))
    warnings.extend(size_warnings)
    if text_or_none(header.get("size")) is None:
        min_size = "M"
        warnings.append("Missing Size; defaulted to M.")
    elif min_size is None:
        errors.append(f"Size must be T, S, M, L, H, C or a range like 'S-M', got {header.get('size')!r}.")

    stats: dict[str, int | None] = {}
    for label, column, required, default in [
        ("hp", "hp", True, None), ("wp", "wp", False, None), ("mp", "mp", False, 0), ("ep", "ep", False, 0),
        ("prio", "prio", False, 0), ("pd", "pd", False, 0), ("md", "md", False, 0),
    ]:
        raw = combat.get(label)
        value = to_int(raw)
        if raw is not None and text_or_none(raw) is not None and value is None:
            errors.append(f"{label.upper()} must be a whole number, got {raw!r}.")
        elif value is None and required:
            errors.append(f"Missing {label.upper()}.")
        stats[column] = value if value is not None else default

    movement_raw = combat.get("mvm", combat.get("mv"))
    movement = to_int(movement_raw)
    if movement_raw is not None and text_or_none(movement_raw) is not None and movement is None:
        errors.append(f"MVM must look like '30ft', got {movement_raw!r}.")
    elif movement is None:
        movement = 30
        warnings.append("Missing MVM; defaulted to 30ft.")

    skills = parse_skills(block.skills_text, errors)

    augments: list[CreatureAugment] = []
    for kind, values in block.augments:
        for raw in values.split(","):
            target = raw.strip().rstrip(".")
            if not target or target.lower() in MISSING_TEXT_VALUES:
                continue
            damage_type = refs.damage_types.get(key(target))
            status_condition = None if damage_type else refs.status_conditions.get(key(target))
            if damage_type is None and status_condition is None:
                errors.append(
                    f"{kind.capitalize()} {target!r} is not a damage type or status condition."
                    + did_you_mean(target, {**refs.damage_types, **refs.status_conditions})
                )
                continue
            augments.append(
                CreatureAugment(
                    kind=kind,
                    target=damage_type or status_condition,
                    damage_type=damage_type,
                    status_condition=status_condition,
                )
            )

    abilities: list[CreatureAbility] = []
    attacks: list[CreatureAttack] = []
    for timing, lines in block.entries:
        if timing == "declared" and re.search(r"(?i)\bdamage\s*:", " ".join(lines)):
            attack = parse_attack(lines, refs, errors)
            if attack:
                attacks.append(attack)
        else:
            abilities.append(parse_ability(timing, lines, threat_lvl or 1, refs, warnings))

    for line in block.stray_lines:
        warnings.append(f"Ignored a line that does not belong to any stat block field: {line[:80]!r}")

    if errors:
        return InvalidCreature(source_text=block.text, errors=errors)

    return CreatureRow(
        name=name,
        creature_type=creature_type,
        threat_lvl=threat_lvl,
        min_size=min_size,
        max_size=max_size,
        description=text_or_none(header.get("description")),
        languages=text_or_none(header.get("languages")),
        lore=" ".join(block.lore).strip() or None,
        movement=movement,
        skills=skills,
        augments=augments,
        abilities=abilities,
        attacks=attacks,
        warnings=warnings,
        **stats,
    )


# ----------------------------
# Extraction pipeline
# ----------------------------

def extract_creatures(
    raw_text: str,
    database_url: str,
    source_id: str,
    refs: ReferenceData | None = None,
) -> tuple[AgentCreatureImportResult, dict[str, Any]]:
    """Parse every stat block in raw_text. Pass refs to reuse reference data across many files."""
    refs = refs or fetch_reference_data(database_url, source_id)
    blocks = [read_block(lines) for lines in split_creature_blocks(raw_text)]

    real_blocks = [block for block in blocks if not is_template(block)]
    metadata: dict[str, Any] = {
        "creature_blocks_found": len(blocks),
        # "Name:" blocks without a numeric threat LVL or HP, such as the template in the rules.
        "skipped_templates": len(blocks) - len(real_blocks),
        "duplicate_count": 0,
        "valid_count": 0,
        "invalid_count": 0,
        "linked_traits": 0,
        "linked_powers": 0,
    }

    if not real_blocks:
        return (
            AgentCreatureImportResult(
                valid_creatures=[],
                invalid_creatures=[
                    InvalidCreature(
                        source_text=raw_text[:4000],
                        errors=["No creatures found. Expected stat blocks starting with 'Name:'."],
                    )
                ],
            ),
            metadata,
        )

    valid: list[CreatureRow] = []
    invalid: list[InvalidCreature] = []
    seen: set[str] = set()

    for block in real_blocks:
        result = build_creature(block, refs)
        if isinstance(result, InvalidCreature):
            invalid.append(result)
            continue
        if key(result.name) in seen:
            metadata["duplicate_count"] += 1
            continue
        seen.add(key(result.name))
        valid.append(result)

    metadata["valid_count"] = len(valid)
    metadata["invalid_count"] = len(invalid)
    metadata["linked_traits"] = sum(1 for c in valid for a in c.abilities if a.trait)
    metadata["linked_powers"] = sum(1 for c in valid for a in c.abilities if a.power)

    return AgentCreatureImportResult(valid_creatures=valid, invalid_creatures=invalid), metadata


# ----------------------------
# Archive (zip of .docx stat blocks)
# ----------------------------

THREAT_FOLDER = re.compile(r"(?i)threat\s*l(?:e)?v(?:e)?l\s*(\d+)")
ARCHIVE_EXTENSIONS = {".docx"}


def folder_threat_level(relative_path: str) -> int | None:
    """Threat level from the nearest 'Threat Level N' folder in the path, if any."""
    for part in reversed(relative_path.split("/")[:-1]):
        match = THREAT_FOLDER.search(part)
        if match:
            return int(match.group(1))
    return None


def extract_creatures_from_archive(
    zip_path: str,
    database_url: str,
    source_id: str,
) -> tuple[AgentCreatureImportResult, list[dict[str, Any]], dict[str, Any]]:
    """
    Run the creature importer on every .docx file in a zip, e.g.

        Main Dir/Threat Level 1/Goblin.docx
        Main Dir/Threat Level 2/Wolf.docx

    Returns (combined result, per-file report, metadata). Each creature's warnings name its file, and a
    creature whose Threat LVL differs from its "Threat Level N" folder gets a warning. Files are parsed
    one at a time, so memory use stays flat for large archives.
    """
    from helpers.archive_helpers import extracted_zip, find_files, relative_name

    refs = fetch_reference_data(database_url, source_id)
    valid: list[CreatureRow] = []
    invalid: list[InvalidCreature] = []
    files: list[dict[str, Any]] = []
    seen: dict[str, str] = {}
    duplicates: list[str] = []

    with extracted_zip(zip_path, extensions=ARCHIVE_EXTENSIONS) as root:
        for path in find_files(root, ARCHIVE_EXTENSIONS):
            name = relative_name(path, root)
            report: dict[str, Any] = {"file": name, "creatures": [], "duplicates": [], "invalid": 0, "error": None}
            files.append(report)

            try:
                text = extract_text(path)
            except Exception as exc:  # unreadable/corrupt .docx: report it and keep going
                report["error"] = f"Could not read the file: {exc}"
                invalid.append(InvalidCreature(source_text=name, errors=[report["error"]]))
                continue

            result, _ = extract_creatures(text, database_url, source_id, refs=refs)
            expected_lvl = folder_threat_level(name)

            for creature in result.valid_creatures:
                creature.warnings.insert(0, f"From {name}.")
                if expected_lvl is not None and creature.threat_lvl != expected_lvl:
                    creature.warnings.append(
                        f"Threat LVL {creature.threat_lvl} does not match its folder (Threat Level {expected_lvl})."
                    )
                if key(creature.name) in seen:
                    duplicates.append(f"{creature.name!r} in {name} (already in {seen[key(creature.name)]})")
                    report["duplicates"].append(creature.name)
                    continue
                seen[key(creature.name)] = name
                valid.append(creature)
                report["creatures"].append(creature.name)

            for bad in result.invalid_creatures:
                bad.errors.insert(0, f"From {name}.")
                invalid.append(bad)
            report["invalid"] = len(result.invalid_creatures)

    metadata = {
        "files_found": len(files),
        # Files with no stat block at all (duplicates, invalid blocks, and read errors are reported separately).
        "files_without_creatures": [
            f["file"] for f in files
            if not f["creatures"] and not f["duplicates"] and not f["invalid"] and not f["error"]
        ],
        "valid_count": len(valid),
        "invalid_count": len(invalid),
        "duplicates_skipped": duplicates,
        "linked_traits": sum(1 for c in valid for a in c.abilities if a.trait),
        "linked_powers": sum(1 for c in valid for a in c.abilities if a.power),
    }
    return AgentCreatureImportResult(valid_creatures=valid, invalid_creatures=invalid), files, metadata


# ----------------------------
# Database insert
# ----------------------------

UPSERT_CREATURE_SQL = """
INSERT INTO creatures (
    source_id, name, creature_type_id, threat_lvl, min_size, max_size, description, languages, lore,
    hp, wp, mp, ep, prio, movement, pd, md, skills
)
VALUES (
    %(source_id)s,
    %(name)s,
    (SELECT id FROM creature_types WHERE source_id = %(source_id)s AND name_key = LOWER(TRIM(%(creature_type)s))),
    %(threat_lvl)s,
    %(min_size)s::size,
    %(max_size)s::size,
    %(description)s,
    %(languages)s,
    %(lore)s,
    %(hp)s, %(wp)s, %(mp)s, %(ep)s, %(prio)s, %(movement)s, %(pd)s, %(md)s,
    %(skills)s
)
ON CONFLICT (name_key)
DO UPDATE SET
    source_id = EXCLUDED.source_id,
    name = EXCLUDED.name,
    creature_type_id = EXCLUDED.creature_type_id,
    threat_lvl = EXCLUDED.threat_lvl,
    min_size = EXCLUDED.min_size,
    max_size = EXCLUDED.max_size,
    description = EXCLUDED.description,
    languages = EXCLUDED.languages,
    lore = EXCLUDED.lore,
    hp = EXCLUDED.hp,
    wp = EXCLUDED.wp,
    mp = EXCLUDED.mp,
    ep = EXCLUDED.ep,
    prio = EXCLUDED.prio,
    movement = EXCLUDED.movement,
    pd = EXCLUDED.pd,
    md = EXCLUDED.md,
    skills = EXCLUDED.skills,
    updated_at = NOW()
RETURNING id;
"""

DELETE_CHILDREN_SQL = [
    "DELETE FROM creature_augments WHERE creature_id = %(creature_id)s;",
    "DELETE FROM creature_abilities WHERE creature_id = %(creature_id)s;",
    # Attack properties cascade from creature_attacks.
    "DELETE FROM creature_attacks WHERE creature_id = %(creature_id)s;",
]

INSERT_AUGMENT_SQL = """
INSERT INTO creature_augments (creature_id, kind, target, damage_type_id, status_condition_id)
VALUES (%(creature_id)s, %(kind)s::augment_kind, %(target)s,
        (SELECT id FROM damage_types WHERE name_key = LOWER(TRIM(%(damage_type)s))),
        (SELECT id FROM status_conditions WHERE name_key = LOWER(TRIM(%(status_condition)s))))
ON CONFLICT (creature_id, kind, target) DO NOTHING;
"""

INSERT_ABILITY_SQL = """
INSERT INTO creature_abilities (
    creature_id, timing, name, effect, trait_id, power_id, lvl, uses, mp_cost, ep_cost, tp_cost, sort_order
)
VALUES (
    %(creature_id)s, %(timing)s::ability_timing, %(name)s, %(effect)s,
    (SELECT id FROM traits WHERE trait_name_key = LOWER(TRIM(%(trait)s))),
    (SELECT id FROM powers WHERE name_key = LOWER(TRIM(%(power)s))),
    %(lvl)s, %(uses)s, %(mp_cost)s, %(ep_cost)s, %(tp_cost)s, %(sort_order)s
);
"""

INSERT_ATTACK_SQL = """
INSERT INTO creature_attacks (
    creature_id, name, skill, range_ft, damage_dice, damage_bonus, damage_type_id, mag, sort_order
)
VALUES (
    %(creature_id)s, %(name)s, %(skill)s, %(range_ft)s, %(damage_dice)s, %(damage_bonus)s,
    (SELECT id FROM damage_types WHERE name_key = LOWER(TRIM(%(damage_type)s))),
    %(mag)s, %(sort_order)s
)
RETURNING id;
"""

INSERT_ATTACK_PROPERTY_SQL = """
INSERT INTO creature_attack_properties (attack_id, property_id)
VALUES (%(attack_id)s, (SELECT id FROM weapon_properties WHERE name_key = LOWER(TRIM(%(property)s))))
ON CONFLICT (attack_id, property_id) DO NOTHING;
"""


def insert_valid_creatures(
    result: AgentCreatureImportResult,
    database_url: str,
    source_id: str,
) -> list[str]:
    """Upserts valid creatures and replaces their augments, abilities, and attacks in one transaction."""
    try:
        import psycopg
        from psycopg.types.json import Jsonb
    except ImportError as exc:
        raise RuntimeError("Missing dependency. Install with: pip install psycopg[binary]") from exc

    creature_ids: list[str] = []

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        with conn.cursor() as cur:
            for creature in result.valid_creatures:
                data = creature.model_dump(mode="json", exclude={"augments", "abilities", "attacks", "warnings"})
                data["skills"] = Jsonb(data["skills"])
                cur.execute(UPSERT_CREATURE_SQL, {"source_id": source_id, **data})
                creature_id = cur.fetchone()[0]

                for sql in DELETE_CHILDREN_SQL:
                    cur.execute(sql, {"creature_id": creature_id})

                for augment in creature.augments:
                    cur.execute(INSERT_AUGMENT_SQL, {"creature_id": creature_id, **augment.model_dump()})

                for order, ability in enumerate(creature.abilities):
                    cur.execute(
                        INSERT_ABILITY_SQL,
                        {"creature_id": creature_id, "sort_order": order, **ability.model_dump()},
                    )

                for order, attack in enumerate(creature.attacks):
                    cur.execute(
                        INSERT_ATTACK_SQL,
                        {"creature_id": creature_id, "sort_order": order, **attack.model_dump(exclude={"properties"})},
                    )
                    attack_id = cur.fetchone()[0]
                    for prop in attack.properties:
                        cur.execute(INSERT_ATTACK_PROPERTY_SQL, {"attack_id": attack_id, "property": prop})

                creature_ids.append(str(creature_id))

        conn.commit()

    return creature_ids
