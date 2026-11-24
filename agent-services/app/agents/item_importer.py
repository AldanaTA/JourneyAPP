"""
item_importer.py

Import parser for the Journey `items`, `item_weapons`, and `item_weapon_properties` tables.

Unlike the power/trait/job/race importers this one does not call a model: every item field is a
labelled line ("Rarity:", "Cost:", "Damage:", ...), so items are parsed deterministically.

Flow:
- Walk the text line by line, tracking the current book section ("Potions", "Tonics", ...)
- An item starts at a name line whose next non-empty line is a known field label
- Read the item's fields; skip blocks that are not items (rules entries, format templates)
- Work out item_type from the fields first, then from the section heading
- Validate against the reference tables (weapon types, weapon properties, damage types)
- Return valid_items and invalid_items
- Insert only valid items using synchronous psycopg
"""

from __future__ import annotations

import re
from collections import Counter
from dataclasses import dataclass, field
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

from agents.trait_importer import normalize_database_url
from helpers.agent_helpers import extract_text, normalize_text

__all__ = [
    "AgentItemImportResult",
    "extract_items",
    "extract_text",
    "insert_valid_items",
]


# ----------------------------
# Enums (mirror backend/schema/06-items.sql)
# ----------------------------

ItemType = Literal[
    "weapon", "armor", "shield", "ammunition", "potion", "accessory", "magic_item",
    "bag", "med_kit", "skill_book", "tool", "alchemy_ingredient", "cooking_ingredient",
    "gem", "metal", "miscellaneous",
]
ItemRarity = Literal["common", "uncommon", "rare", "very_rare"]

RARITY_VALUES = {"common", "uncommon", "rare", "very_rare"}
MISSING_TEXT_VALUES = {"", "-", "none", "n/a", "null"}

# Bronze per unit of each denomination.
CURRENCY_RATES = {
    "bronze": 1,
    "silver": 10,
    "gold": 100,
    "platinum": 10_000,
}

# Item types whose name may start with a metal ("Iron Long Sword") and so get a material.
MATERIAL_ITEM_TYPES = {"weapon", "armor", "shield"}


def key(value: str) -> str:
    """Case/whitespace/quote-insensitive matching key for names."""
    text = value.replace("’", "'").replace("‘", "'")
    return re.sub(r"\s+", " ", text).strip().lower()


# ----------------------------
# Pydantic models
# ----------------------------

class WeaponStats(BaseModel):
    model_config = ConfigDict(extra="forbid")

    weapon_type: str
    damage_dice: str
    damage_bonus: str | None = None
    damage_type: str
    range_ft: int | None = None
    mag: int | None = None
    properties: list[str] = Field(default_factory=list)


class ItemRow(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1)
    item_type: ItemType
    rarity: ItemRarity
    is_magic: bool = False
    description: str | None = None
    effect: str | None = None
    cost_bronze: int | None = Field(default=None, ge=0)
    stack_limit: int = Field(default=1, ge=1)
    bundle_quantity: int = Field(default=1, ge=1)
    requires_attunement: bool = False
    charges: int | None = Field(default=None, ge=0)
    details: dict[str, Any] = Field(default_factory=dict)
    # Name of the metal item this is made of (resolved to material_id on insert).
    material: str | None = None
    weapon: WeaponStats | None = None
    # Non-blocking notes for the preview (defaults applied, unknown section, ...).
    warnings: list[str] = Field(default_factory=list)


class InvalidItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source_text: str
    errors: list[str]


class AgentItemImportResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    valid_items: list[ItemRow]
    invalid_items: list[InvalidItem]


# ----------------------------
# Book structure
# ----------------------------

# Section headings -> (item_type, details implied by the section). None = a section with no items
# of its own (its type comes from sub-sections or from the item's fields).
SECTION_TYPES: dict[str, tuple[str, dict[str, Any]] | None] = {
    "inventory": None,
    "items": None,
    "currency": None,
    "tools": ("tool", {}),
    "bags": ("bag", {}),
    "med kits": ("med_kit", {}),
    "skill books": ("skill_book", {}),
    "mundane items": ("miscellaneous", {}),
    "ammunition": ("ammunition", {}),
    "arrows": ("ammunition", {"ammo_type": "arrow"}),
    "bullets": ("ammunition", {"ammo_type": "bullet"}),
    "armor": ("armor", {}),
    "shields": ("shield", {}),
    "weapons": ("weapon", {}),
    "arcane focus": ("weapon", {}),
    "bows": ("weapon", {}),
    "colossal weapons": ("weapon", {}),
    "firearms": ("weapon", {}),
    "martial weapons": ("weapon", {}),
    "simple weapons": ("weapon", {}),
    "weapon properties": None,
    "potions": ("potion", {}),
    "magic items": ("magic_item", {}),
    "accessories": ("accessory", {}),
    "alchemy": ("alchemy_ingredient", {}),
    "tonics": ("alchemy_ingredient", {"role": "tonic"}),
    "resistances": ("alchemy_ingredient", {"role": "resistance"}),
    "poisons": ("alchemy_ingredient", {"role": "poison"}),
    "enhancer": ("alchemy_ingredient", {"role": "enhancer"}),
    "enhancers": ("alchemy_ingredient", {"role": "enhancer"}),
    "cooking": ("cooking_ingredient", {}),
    "smithing": None,
    "gems": ("gem", {}),
    "metals": ("metal", {}),
}

FIELD_LABEL = re.compile(
    r"(?i)^(description|rarity|stackable|effect|cost|charges|attunement|strength req|"
    r"weapon type|weapon properties|range|damage|mag|skills|skill bonus|sessions|"
    r"magic item effect|armor effect|weapon effect|defense)\s*:\s*(.*)$"
)

# Fields whose text can wrap onto following lines.
LONG_TEXT_FIELDS = {"description", "effect", "magic item effect", "armor effect", "weapon effect"}


def section_key(line: str) -> str:
    return key(line).rstrip(":").strip()


def is_heading(line: str) -> bool:
    return section_key(line) in SECTION_TYPES


def is_possible_item_name(line: str) -> bool:
    cleaned = line.strip()
    if not cleaned or len(cleaned) > 80:
        return False
    if ":" in cleaned:
        # Item names never contain a colon; "BookName:" templates and labels do.
        return False
    if cleaned[-1] in ".!?":
        # Item names never end like a sentence; wrapped field text does
        # (e.g. Ruby's "Choose the resource when the item is crafted." before "Cost:").
        return False
    return bool(re.search(r"[A-Za-z]", cleaned))


@dataclass
class ItemBlock:
    name: str
    text: str
    section: str | None
    fields: dict[str, str] = field(default_factory=dict)
    duplicate_labels: list[str] = field(default_factory=list)


def next_nonempty(lines: list[str], start: int) -> int | None:
    for i in range(start, len(lines)):
        if lines[i].strip():
            return i
    return None


def is_item_start(lines: list[str], i: int) -> bool:
    if not is_possible_item_name(lines[i]) or is_heading(lines[i]):
        return False
    nxt = next_nonempty(lines, i + 1)
    return nxt is not None and bool(FIELD_LABEL.match(lines[nxt].strip()))


def split_item_blocks(raw_text: str) -> list[ItemBlock]:
    """
    Splits the book into item blocks while tracking which section each item is in.

    Does not depend on blank lines (the .docx reader drops them): a block ends at the next
    item start, at a section heading, or at the first unlabeled line that does not continue
    a wrapping text field.
    """
    lines = [line.rstrip() for line in normalize_text(raw_text).split("\n")]
    blocks: list[ItemBlock] = []
    section: str | None = None
    i = 0

    while i < len(lines):
        line = lines[i].strip()

        if is_heading(line):
            section = section_key(line)
            i += 1
            continue

        if not is_item_start(lines, i):
            i += 1
            continue

        block = ItemBlock(name=line, text="", section=section)
        block_lines = [line]
        last_label: str | None = None
        j = i + 1

        while j < len(lines):
            current = lines[j].strip()

            if not current:
                j += 1
                continue

            match = FIELD_LABEL.match(current)
            if match:
                label = re.sub(r"\s+", " ", match.group(1).lower())
                if label in block.fields:
                    block.duplicate_labels.append(label)
                else:
                    block.fields[label] = match.group(2).strip()
                last_label = label
                block_lines.append(current)
                j += 1
                continue

            if is_heading(current) or is_item_start(lines, j):
                break

            if last_label in LONG_TEXT_FIELDS:
                # Keep the source's line breaks (option lists, paragraphs).
                block.fields[last_label] = f"{block.fields[last_label]}\n{current}".strip()
                block_lines.append(current)
                j += 1
                continue

            break

        block.text = "\n".join(block_lines)
        blocks.append(block)
        i = j

    return blocks


def is_real_item(block: ItemBlock) -> bool:
    """
    Rules entries (damage types: "Acid / Effect / Defense") and format templates
    ("Name / Rarity: / Cost:" with no values) look like items; real items have a rarity or a cost.
    """
    return bool(block.fields.get("rarity", "").strip() or block.fields.get("cost", "").strip())


# ----------------------------
# Field parsing
# ----------------------------

def text_or_none(value: str | None) -> str | None:
    if value is None:
        return None
    cleaned = value.replace("\x00", "").strip()
    return None if cleaned.lower() in MISSING_TEXT_VALUES else cleaned


def parse_cost(value: str | None) -> tuple[int | None, str | None]:
    """Returns (cost_in_bronze, error). "20 Gold" -> 2000, "5 silver" -> 50, "0" -> 0."""
    text = text_or_none(value)
    if text is None:
        return None, None

    match = re.fullmatch(r"([\d,]*\.?\d+)\s*([A-Za-z]+)?\s*\.?", text)
    if not match:
        return None, f"Cost must look like '20 Gold', got: {value!r}."

    amount = float(match.group(1).replace(",", ""))
    unit = (match.group(2) or "").lower()

    if not unit:
        if amount == 0:
            return 0, None
        return None, f"Cost {value!r} has no currency (Bronze, Silver, Gold, Platinum)."

    unit = unit.rstrip("s") if unit.rstrip("s") in CURRENCY_RATES else unit
    if unit not in CURRENCY_RATES:
        return None, f"Unknown currency in cost {value!r}. Use Bronze, Silver, Gold, or Platinum."

    return round(amount * CURRENCY_RATES[unit]), None


def parse_int(value: str | None, label: str) -> tuple[int | None, str | None]:
    text = text_or_none(value)
    if text is None:
        return None, None
    match = re.fullmatch(r"(\d+)\s*(?:ft\.?|feet|%)?\.?", text, re.IGNORECASE)
    if not match:
        return None, f"{label} must be a whole number, got: {value!r}."
    return int(match.group(1)), None


def parse_rarity(value: str | None) -> str | None:
    text = text_or_none(value)
    if text is None:
        return None
    rarity = re.sub(r"\s+", "_", text.strip().lower())
    return rarity if rarity in RARITY_VALUES else None


def split_bundle(name: str) -> tuple[str, int]:
    """'Standard Arrows x10' -> ('Standard Arrows', 10)."""
    match = re.fullmatch(r"(.*?)\s+x(\d+)", name.strip(), re.IGNORECASE)
    if match:
        return match.group(1).strip(), int(match.group(2))
    return name.strip(), 1


DAMAGE_PATTERN = re.compile(
    r"(?i)^(\d+d\d+)\s*(?:\+\s*([A-Za-z0-9]+))?\s+([A-Za-z]+)(?:\s+damage)?\s*\.?$"
)
MODIFIER_PATTERN = re.compile(r"([+-]\d+)%\s+([A-Za-z][A-Za-z ]*?)(?=\s*(?:,|\.|$))")


def parse_modifiers(effect: str | None) -> list[dict[str, Any]]:
    """'+20% Dodge, +10% Parry' -> [{'skill': 'Dodge', 'value': 20}, {'skill': 'Parry', 'value': 10}]."""
    if not effect:
        return []
    return [
        {"skill": skill.strip(), "value": int(value)}
        for value, skill in MODIFIER_PATTERN.findall(effect)
    ]


def infer_item_type(block: ItemBlock) -> tuple[str, dict[str, Any], list[str]]:
    """Returns (item_type, section_details, warnings). Fields win over the section heading."""
    fields = block.fields
    section = SECTION_TYPES.get(block.section or "")
    section_type, section_details = (section if section else (None, {}))
    name = key(block.name)

    if "weapon type" in fields or "damage" in fields:
        return "weapon", {}, []
    if "skills" in fields or "sessions" in fields:
        return "skill_book", {}, []
    if "armor effect" in fields or "weapon effect" in fields:
        return "metal", {}, []
    if "magic item effect" in fields:
        return "gem", {}, []
    if "strength req" in fields:
        if section_type == "shield" or "shield" in name:
            return "shield", {}, []
        return "armor", {}, []
    if section_type:
        return section_type, dict(section_details), []
    if "attunement" in fields:
        return "magic_item", {}, []

    return "miscellaneous", {}, [
        f"Could not tell the item type from its fields or section ({block.section!r}); "
        "stored as miscellaneous."
    ]


@dataclass
class ReferenceData:
    weapon_types: dict[str, str]        # key -> display name
    weapon_properties: dict[str, str]
    damage_types: dict[str, str]
    metals: dict[str, str]              # key -> metal item name (from the database)


def parse_weapon(block: ItemBlock, refs: ReferenceData, errors: list[str]) -> WeaponStats | None:
    fields = block.fields

    weapon_type_raw = text_or_none(fields.get("weapon type"))
    weapon_type = refs.weapon_types.get(key(weapon_type_raw or ""))
    if weapon_type_raw is None:
        errors.append("Missing Weapon Type.")
    elif weapon_type is None:
        errors.append(
            f"Unknown weapon type {weapon_type_raw!r}. Known: {', '.join(sorted(refs.weapon_types.values()))}."
        )

    damage_raw = text_or_none(fields.get("damage"))
    damage_match = DAMAGE_PATTERN.match(damage_raw or "")
    damage_type = None
    if damage_raw is None:
        errors.append("Missing Damage.")
    elif not damage_match:
        errors.append(f"Damage must look like '1d8 + PD slashing', got: {damage_raw!r}.")
    else:
        damage_type = refs.damage_types.get(key(damage_match.group(3)))
        if damage_type is None:
            errors.append(f"Unknown damage type {damage_match.group(3)!r} in {damage_raw!r}.")

    properties: list[str] = []
    for raw in (text_or_none(fields.get("weapon properties")) or "").split(","):
        if not raw.strip():
            continue
        prop = refs.weapon_properties.get(key(raw))
        if prop is None:
            errors.append(f"Unknown weapon property {raw.strip()!r}.")
        elif prop not in properties:
            properties.append(prop)

    range_ft, error = parse_int(fields.get("range"), "Range")
    if error:
        errors.append(error)
    mag, error = parse_int(fields.get("mag"), "Mag")
    if error:
        errors.append(error)

    if errors or not damage_match or weapon_type is None or damage_type is None:
        return None

    return WeaponStats(
        weapon_type=weapon_type,
        damage_dice=damage_match.group(1).lower(),
        damage_bonus=damage_match.group(2),
        damage_type=damage_type,
        range_ft=range_ft,
        mag=mag,
        properties=properties,
    )


def build_item(block: ItemBlock, refs: ReferenceData, metal_names: dict[str, str]) -> ItemRow | InvalidItem:
    fields = block.fields
    errors: list[str] = []
    warnings: list[str] = [f"Field {label!r} appears more than once; kept the first." for label in block.duplicate_labels]

    name, bundle_quantity = split_bundle(block.name)
    item_type, details, type_warnings = infer_item_type(block)
    warnings.extend(type_warnings)

    rarity = parse_rarity(fields.get("rarity"))
    if text_or_none(fields.get("rarity")) is None:
        rarity = "common"
        warnings.append("Missing Rarity; defaulted to common.")
    elif rarity is None:
        errors.append(f"Rarity must be Common, Uncommon, Rare, or Very Rare, got: {fields['rarity']!r}.")

    cost_bronze, error = parse_cost(fields.get("cost"))
    if error:
        errors.append(error)
    elif cost_bronze is None:
        warnings.append("No listed cost.")

    stack_limit, error = parse_int(fields.get("stackable"), "Stackable")
    if error:
        errors.append(error)

    charges = None
    charges_raw = text_or_none(fields.get("charges"))
    if charges_raw is not None and charges_raw.lower() != "passive":
        charges, error = parse_int(charges_raw, "Charges")
        if error:
            errors.append(error)

    requires_attunement = False
    attunement_raw = text_or_none(fields.get("attunement"))
    if attunement_raw is not None:
        if attunement_raw.lower() in {"yes", "y", "true", "required"}:
            requires_attunement = True
        elif attunement_raw.lower() not in {"no", "n", "false"}:
            errors.append(f"Attunement must be Yes or No, got: {attunement_raw!r}.")

    effect = text_or_none(fields.get("effect"))
    weapon = None

    if item_type == "weapon":
        weapon = parse_weapon(block, refs, errors)

    elif item_type in {"armor", "shield"}:
        strength_req, error = parse_int(fields.get("strength req"), "Strength Req")
        if error:
            errors.append(error)
        details["strength_req"] = strength_req
        details["modifiers"] = parse_modifiers(effect)

    elif item_type == "ammunition":
        if "ammo_type" not in details:
            if "arrow" in key(name):
                details["ammo_type"] = "arrow"
            elif "bullet" in key(name):
                details["ammo_type"] = "bullet"

    elif item_type == "bag":
        match = re.search(r"(\d+)\s+inventory slots?", effect or "", re.IGNORECASE)
        if match:
            details["inventory_slots"] = int(match.group(1))
        else:
            warnings.append("Could not find the number of inventory slots in the effect.")

    elif item_type == "skill_book":
        details["skills"] = [s.strip() for s in (text_or_none(fields.get("skills")) or "").split(",") if s.strip()]
        skill_bonus, error = parse_int(fields.get("skill bonus"), "Skill Bonus")
        if error:
            errors.append(error)
        sessions, error = parse_int(fields.get("sessions"), "Sessions")
        if error:
            errors.append(error)
        details["skill_bonus"] = skill_bonus
        details["sessions"] = sessions

    elif item_type == "tool":
        for discipline in ("alchemy", "enchanting", "smithing"):
            if discipline in key(name):
                details["discipline"] = discipline
                break

    elif item_type == "gem":
        details["magic_item_effect"] = text_or_none(fields.get("magic item effect"))

    elif item_type == "metal":
        details["armor_effect"] = text_or_none(fields.get("armor effect"))
        details["weapon_effect"] = text_or_none(fields.get("weapon effect"))

    material = None
    if item_type in MATERIAL_ITEM_TYPES:
        # Longest metal name first, so "Darkened Iron Sword" is not read as "Iron".
        for metal_key in sorted(metal_names, key=len, reverse=True):
            if key(name).startswith(metal_key + " "):
                material = metal_names[metal_key]
                break

    if errors:
        return InvalidItem(source_text=block.text, errors=errors)

    return ItemRow(
        name=name,
        item_type=item_type,
        rarity=rarity,
        is_magic=item_type in {"magic_item", "accessory"} or attunement_raw is not None,
        description=text_or_none(fields.get("description")),
        effect=effect,
        cost_bronze=cost_bronze,
        stack_limit=stack_limit or 1,
        bundle_quantity=bundle_quantity,
        requires_attunement=requires_attunement,
        charges=charges,
        details={k: v for k, v in details.items() if v is not None},
        material=material,
        weapon=weapon,
        warnings=warnings,
    )


# ----------------------------
# Reference lookup
# ----------------------------

def fetch_reference_data(database_url: str) -> ReferenceData:
    import psycopg

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        with conn.cursor() as cur:
            def names(sql: str) -> dict[str, str]:
                cur.execute(sql)
                return {key(row[0]): row[0] for row in cur.fetchall()}

            return ReferenceData(
                weapon_types=names("SELECT name FROM weapon_types;"),
                weapon_properties=names("SELECT name FROM weapon_properties;"),
                damage_types=names("SELECT name FROM damage_types;"),
                metals=names("SELECT name FROM items WHERE item_type = 'metal';"),
            )


# ----------------------------
# Extraction pipeline
# ----------------------------

def extract_items(raw_text: str, database_url: str) -> tuple[AgentItemImportResult, dict[str, Any]]:
    refs = fetch_reference_data(database_url)
    blocks = split_item_blocks(raw_text)

    real_blocks = [block for block in blocks if is_real_item(block)]
    skipped = [block.name for block in blocks if not is_real_item(block)]

    metadata: dict[str, Any] = {
        "item_blocks_found": len(blocks),
        # Blocks that look like items but have no rarity or cost: rules entries and format templates.
        "skipped_blocks": skipped,
        "duplicate_count": 0,
        "valid_count": 0,
        "invalid_count": 0,
        "by_type": {},
    }

    if not real_blocks:
        return (
            AgentItemImportResult(
                valid_items=[],
                invalid_items=[
                    InvalidItem(
                        source_text=raw_text[:4000],
                        errors=[
                            "No items found. Expected an item name line followed by labelled fields "
                            "such as 'Rarity:' and 'Cost:'."
                        ],
                    )
                ],
            ),
            metadata,
        )

    # Metals in this file count as materials too (they are inserted before the items that use them).
    metal_names = dict(refs.metals)
    for block in real_blocks:
        if infer_item_type(block)[0] == "metal":
            metal_names[key(block.name)] = block.name.strip()

    valid_items: list[ItemRow] = []
    invalid_items: list[InvalidItem] = []
    seen: set[str] = set()

    for block in real_blocks:
        result = build_item(block, refs, metal_names)

        if isinstance(result, InvalidItem):
            invalid_items.append(result)
            continue

        if key(result.name) in seen:
            metadata["duplicate_count"] += 1
            continue

        seen.add(key(result.name))
        valid_items.append(result)

    metadata["valid_count"] = len(valid_items)
    metadata["invalid_count"] = len(invalid_items)
    metadata["by_type"] = dict(Counter(item.item_type for item in valid_items))

    return AgentItemImportResult(valid_items=valid_items, invalid_items=invalid_items), metadata


# ----------------------------
# Database insert
# ----------------------------

# base_item_id is left alone on re-import: the book has no derived items, and items derived
# later (e.g. a magic sword based on Iron Long Sword) must keep their link.
UPSERT_ITEM_SQL = """
INSERT INTO items (
    source_id, name, item_type, material_id, is_magic, rarity, description, effect,
    cost_bronze, stack_limit, bundle_quantity, requires_attunement, charges, details
)
VALUES (
    %(source_id)s,
    %(name)s,
    %(item_type)s::item_type,
    (SELECT id FROM items WHERE name_key = LOWER(TRIM(%(material)s)) AND item_type = 'metal'),
    %(is_magic)s,
    %(rarity)s::item_rarity,
    %(description)s,
    %(effect)s,
    %(cost_bronze)s,
    %(stack_limit)s,
    %(bundle_quantity)s,
    %(requires_attunement)s,
    %(charges)s,
    %(details)s
)
ON CONFLICT (name_key)
DO UPDATE SET
    source_id = EXCLUDED.source_id,
    name = EXCLUDED.name,
    item_type = EXCLUDED.item_type,
    material_id = EXCLUDED.material_id,
    is_magic = EXCLUDED.is_magic,
    rarity = EXCLUDED.rarity,
    description = EXCLUDED.description,
    effect = EXCLUDED.effect,
    cost_bronze = EXCLUDED.cost_bronze,
    stack_limit = EXCLUDED.stack_limit,
    bundle_quantity = EXCLUDED.bundle_quantity,
    requires_attunement = EXCLUDED.requires_attunement,
    charges = EXCLUDED.charges,
    details = EXCLUDED.details,
    updated_at = NOW()
RETURNING id;
"""

UPSERT_WEAPON_SQL = """
INSERT INTO item_weapons (item_id, weapon_type_id, damage_dice, damage_bonus, damage_type_id, range_ft, mag)
VALUES (%(item_id)s, %(weapon_type_id)s, %(damage_dice)s, %(damage_bonus)s, %(damage_type_id)s, %(range_ft)s, %(mag)s)
ON CONFLICT (item_id)
DO UPDATE SET
    weapon_type_id = EXCLUDED.weapon_type_id,
    damage_dice = EXCLUDED.damage_dice,
    damage_bonus = EXCLUDED.damage_bonus,
    damage_type_id = EXCLUDED.damage_type_id,
    range_ft = EXCLUDED.range_ft,
    mag = EXCLUDED.mag,
    updated_at = NOW();
"""

DELETE_WEAPON_SQL = "DELETE FROM item_weapons WHERE item_id = %(item_id)s;"
DELETE_WEAPON_PROPERTIES_SQL = "DELETE FROM item_weapon_properties WHERE item_id = %(item_id)s;"

INSERT_WEAPON_PROPERTY_SQL = """
INSERT INTO item_weapon_properties (item_id, property_id)
VALUES (%(item_id)s, %(property_id)s)
ON CONFLICT (item_id, property_id) DO NOTHING;
"""


def insert_valid_items(
    result: AgentItemImportResult,
    database_url: str,
    source_id: str,
) -> list[str]:
    """Upserts valid items (metals first, so materials resolve) in a single transaction."""
    try:
        import psycopg
        from psycopg.types.json import Jsonb
    except ImportError as exc:
        raise RuntimeError("Missing dependency. Install with: pip install psycopg[binary]") from exc

    item_ids: list[str] = []
    ordered = sorted(result.valid_items, key=lambda item: item.item_type != "metal")

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        with conn.cursor() as cur:
            def ids(sql: str) -> dict[str, Any]:
                cur.execute(sql)
                return {key(name): row_id for row_id, name in cur.fetchall()}

            weapon_type_ids = ids("SELECT id, name FROM weapon_types;")
            property_ids = ids("SELECT id, name FROM weapon_properties;")
            damage_type_ids = ids("SELECT id, name FROM damage_types;")

            for item in ordered:
                data = item.model_dump(mode="json", exclude={"weapon", "warnings"})
                data["details"] = Jsonb(data["details"])
                cur.execute(UPSERT_ITEM_SQL, {"source_id": source_id, **data})
                item_id = cur.fetchone()[0]

                # Replace weapon stats and properties (or clear them if the item is no longer a weapon).
                cur.execute(DELETE_WEAPON_PROPERTIES_SQL, {"item_id": item_id})

                if item.weapon is None:
                    cur.execute(DELETE_WEAPON_SQL, {"item_id": item_id})
                else:
                    cur.execute(
                        UPSERT_WEAPON_SQL,
                        {
                            "item_id": item_id,
                            "weapon_type_id": weapon_type_ids[key(item.weapon.weapon_type)],
                            "damage_dice": item.weapon.damage_dice,
                            "damage_bonus": item.weapon.damage_bonus,
                            "damage_type_id": damage_type_ids[key(item.weapon.damage_type)],
                            "range_ft": item.weapon.range_ft,
                            "mag": item.weapon.mag,
                        },
                    )
                    for prop in item.weapon.properties:
                        cur.execute(
                            INSERT_WEAPON_PROPERTY_SQL,
                            {"item_id": item_id, "property_id": property_ids[key(prop)]},
                        )

                item_ids.append(str(item_id))

        conn.commit()

    return item_ids
