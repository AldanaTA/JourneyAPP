# agents/trait_importer.py

from __future__ import annotations

import argparse
import asyncio
import json
import os
import re
from concurrent.futures import ThreadPoolExecutor, as_completed
from enum import Enum
from pathlib import Path
from typing import Any

import asyncpg
from openai import OpenAI
from pydantic import BaseModel, Field, field_validator, model_validator


try:
    from helpers import extract_text, normalize_text
except ImportError:
    def normalize_text(text: str) -> str:
        return text.replace("\r\n", "\n").replace("\r", "\n").strip()

    def extract_text(path: str | Path) -> str:
        return Path(path).read_text(encoding="utf-8")


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
    reaction_timing = "reaction_timing"
    status_ailments = "status_ailments"
    environmental = "environmental"
    passive = "passive"
    declared = "declared"
    triggered = "triggered"
    other = "other"


VALID_CATEGORIES = {category.value for category in TraitCategory}
MISSING_TEXT_VALUES = {"", "-", "none", "n/a", "null"}


class TraitImportRow(BaseModel):
    trait_name: str
    lvl: int = 1
    purchase_cost: int = 0
    lvl_up_cost: int = 0
    trait_desc: str = "None"
    trait_uses: int = 0
    mp_cost: int = 0
    ep_cost: int = 0
    tp_cost: int = 0
    trait_effect: str = "None"
    lvl_up_effect: str = "None"
    categories: list[TraitCategory] = Field(default_factory=list)

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
    def parse_int_field(cls, value: Any) -> int:
        if value is None:
            return 0

        if isinstance(value, int):
            return max(value, 0)

        text = str(value).strip().lower()

        if text in MISSING_TEXT_VALUES:
            return 0

        match = re.search(r"\d+", text)
        return int(match.group(0)) if match else 0

    @field_validator(
        "trait_name",
        "trait_desc",
        "trait_effect",
        "lvl_up_effect",
        mode="before",
    )
    @classmethod
    def clean_text_field(cls, value: Any) -> str:
        if value is None:
            return "None"

        text = str(value).strip()
        return text if text else "None"

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

    @model_validator(mode="after")
    def apply_category_fallbacks(self):
        self.categories = infer_trait_categories(self)
        return self


class RawTraitImportResult(BaseModel):
    traits: list[dict[str, Any]] = Field(default_factory=list)


class InvalidTraitRow(BaseModel):
    raw_trait: dict[str, Any]
    reasons: list[str]


class AgentTraitImportResult(BaseModel):
    valid_traits: list[TraitImportRow] = Field(default_factory=list)
    invalid_traits: list[InvalidTraitRow] = Field(default_factory=list)


def is_missing_text(value: Any) -> bool:
    if value is None:
        return True

    return str(value).strip().lower() in MISSING_TEXT_VALUES


def parse_int_for_validation(value: Any) -> int | None:
    if value is None:
        return None

    if isinstance(value, int):
        return value

    text = str(value).strip().lower()

    if text in MISSING_TEXT_VALUES:
        return None

    match = re.search(r"\d+", text)
    return int(match.group(0)) if match else None


def validate_raw_trait(
    raw_trait: dict[str, Any],
) -> tuple[TraitImportRow | None, InvalidTraitRow | None]:
    reasons: list[str] = []

    if is_missing_text(raw_trait.get("trait_name")):
        reasons.append("Missing trait_name.")

    lvl = parse_int_for_validation(raw_trait.get("lvl"))
    if lvl is None:
        reasons.append("Missing lvl.")
    elif lvl < 1:
        reasons.append("lvl must be 1 or greater.")

    if is_missing_text(raw_trait.get("trait_effect")):
        reasons.append("Missing trait_effect.")

    raw_trait.setdefault("purchase_cost", 0)
    raw_trait.setdefault("lvl_up_cost", 0)
    raw_trait.setdefault("trait_desc", "None")
    raw_trait.setdefault("trait_uses", 0)
    raw_trait.setdefault("mp_cost", 0)
    raw_trait.setdefault("ep_cost", 0)
    raw_trait.setdefault("tp_cost", 0)
    raw_trait.setdefault("lvl_up_effect", "None")
    raw_trait.setdefault("categories", ["other"])

    if reasons:
        return None, InvalidTraitRow(
            raw_trait=raw_trait,
            reasons=reasons,
        )

    try:
        trait = TraitImportRow.model_validate(raw_trait)
        return trait, None

    except Exception as exc:
        return None, InvalidTraitRow(
            raw_trait=raw_trait,
            reasons=[f"Pydantic validation failed: {exc}"],
        )


def add_category(categories: list[TraitCategory], category: TraitCategory) -> None:
    if category not in categories:
        categories.append(category)


def infer_trait_categories(trait: TraitImportRow) -> list[TraitCategory]:
    categories = list(trait.categories)

    text = f"""
    {trait.trait_name}
    {trait.trait_desc}
    {trait.trait_effect}
    {trait.lvl_up_effect}
    """.lower()

    if re.search(r"\bdamage\b|\bd\d+\b|\bpd\b|\bmd\b|weapon die", text):
        add_category(categories, TraitCategory.damage)

    if re.search(r"resistance|armor|shield|thp|temp hp|protect|reduce damage|defense", text):
        add_category(categories, TraitCategory.defense)

    if re.search(r"heal|healing|recover|recovery|restore|regain hp|regenerate", text):
        add_category(categories, TraitCategory.healing_recovery)

    if re.search(r"bonus|favorable|increase|empower|advantage|buff", text):
        add_category(categories, TraitCategory.buff)

    if re.search(r"penalty|unfavorable|weakened|reduce|debuff", text):
        add_category(categories, TraitCategory.debuff)

    if re.search(r"stun|stunned|restrain|restrained|prone|pull|push|move.*target|unconscious|incapacitated|dazed", text):
        add_category(categories, TraitCategory.control)

    if re.search(r"movement|speed|dash|jump|climb|swim|fly|teleport|move", text):
        add_category(categories, TraitCategory.mobility)

    if re.search(r"weapon|attack|melee|ranged|missile|parry|block|strike", text):
        add_category(categories, TraitCategory.weapon)

    if re.search(r"magic|spell|cast|power|mp\b|arcane|psychic", text):
        add_category(categories, TraitCategory.magic)

    if re.search(r"summon|companion|familiar|minion|soldier|army|corpse|pet", text):
        add_category(categories, TraitCategory.summoning_companions)

    if re.search(r"transform|form|shape|mutate|mutation", text):
        add_category(categories, TraitCategory.transformation)

    if re.search(r"\btp\b|\bmp\b|\bep\b|resource|regain|restore|cost|uses?", text):
        add_category(categories, TraitCategory.resource_economy)

    if re.search(r"skill roll|skill|bonus to .* roll|penalty to .* roll", text):
        add_category(categories, TraitCategory.skill)

    if re.search(r"explore|travel|utility|search|scout|track|survival|downtime", text):
        add_category(categories, TraitCategory.utility_exploration)

    if re.search(r"craft|item|potion|tool|material|repair|create", text):
        add_category(categories, TraitCategory.crafting_items)

    if re.search(r"stealth|hide|deception|disguise|sneak|unseen", text):
        add_category(categories, TraitCategory.stealth_deception)

    if re.search(r"social|roleplay|persuade|intimidate|lie|convince|interrogate", text):
        add_category(categories, TraitCategory.social_roleplay)

    if re.search(r"reaction|trigger|when|whenever|after|before|start of the round|end of the round|on a hit|on a failure|on a success", text):
        add_category(categories, TraitCategory.triggered)
        add_category(categories, TraitCategory.reaction_timing)

    if re.search(r"status ailment|dazed|incapacitated|restrained|stunned|unconscious|weakened", text):
        add_category(categories, TraitCategory.status_ailments)

    if re.search(r"terrain|environment|light|darkness|weather|hazard|area", text):
        add_category(categories, TraitCategory.environmental)

    if trait.tp_cost > 0 or trait.mp_cost > 0 or trait.ep_cost > 0:
        add_category(categories, TraitCategory.declared)

    if re.search(r"spend a use|activate this trait|you can spend|as an action|resource cost", text):
        add_category(categories, TraitCategory.declared)

    has_activation = (
        trait.trait_uses > 0
        or trait.tp_cost > 0
        or trait.mp_cost > 0
        or trait.ep_cost > 0
        or TraitCategory.declared in categories
        or TraitCategory.triggered in categories
    )

    if not has_activation:
        add_category(categories, TraitCategory.passive)

    if not categories:
        categories.append(TraitCategory.other)

    return categories


def split_trait_blocks(text: str) -> list[str]:
    text = normalize_text(text)

    lines = text.splitlines()
    starts: list[int] = []

    for index in range(len(lines) - 1):
        current = lines[index].strip()
        next_line = lines[index + 1].strip()

        if not current:
            continue

        is_trait_name_label = re.match(r"(?i)^trait name\s*:", current)
        is_name_before_lvl = ":" not in current and re.match(r"(?i)^lvl\s*:", next_line)

        if is_trait_name_label or is_name_before_lvl:
            starts.append(index)

    if not starts:
        return [text]

    blocks: list[str] = []

    for i, start in enumerate(starts):
        end = starts[i + 1] if i + 1 < len(starts) else len(lines)
        block = "\n".join(lines[start:end]).strip()

        if re.search(r"(?i)\blvl\s*:", block) and re.search(r"(?i)\beffect\s*:", block):
            blocks.append(block)

    return blocks or [text]


def batch_blocks(blocks: list[str], max_chars: int) -> list[str]:
    batches: list[str] = []
    current: list[str] = []
    current_size = 0

    for block in blocks:
        block_size = len(block)

        if current and current_size + block_size > max_chars:
            batches.append("\n\n---\n\n".join(current))
            current = []
            current_size = 0

        current.append(block)
        current_size += block_size

    if current:
        batches.append("\n\n---\n\n".join(current))

    return batches


def json_loads_safe(text: str) -> dict[str, Any]:
    text = text.strip()

    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?", "", text).strip()
        text = re.sub(r"```$", "", text).strip()

    return json.loads(text)


SYSTEM_PROMPT = f"""
You are a Journey TTRPG trait import agent.

Extract trait blocks into clean database-ready JSON.

Return only valid JSON in this exact shape:
{{
  "traits": [
    {{
      "trait_name": "string",
      "lvl": 1,
      "purchase_cost": 0,
      "lvl_up_cost": 0,
      "trait_desc": "string",
      "trait_uses": 0,
      "mp_cost": 0,
      "ep_cost": 0,
      "tp_cost": 0,
      "trait_effect": "string",
      "lvl_up_effect": "string",
      "categories": ["damage", "declared"]
    }}
  ]
}}

Rules:
- Ignore headings, section labels, notes, and examples that are not actual traits.
- Convert EXP costs to integers.
- Convert "None", "-", blank, or missing numeric values to 0.
- If a trait has "Uses: 3", set trait_uses to 3.
- If a trait has "Resource Cost: 15EP 2TP", set ep_cost to 15 and tp_cost to 2.
- If a cost is variable or nonnumeric, set the numeric cost to 0 and preserve the detail in trait_effect.
- Use "None" for missing lvl_up_effect.
- Assign 2 to 5 useful categories when possible.
- Use only these category values:
{", ".join(sorted(VALID_CATEGORIES))}
- Timing categories:
  - passive = always-on trait with no activation.
  - declared = player chooses to activate/spend a use/resource/action.
  - triggered = happens when a condition occurs, such as reaction, on hit, start of round, end of round, when damaged, etc.
"""


def extract_trait_batch_with_agent(batch_text: str, model: str) -> AgentTraitImportResult:
    client = OpenAI()

    response = client.chat.completions.create(
        model=model,
        temperature=0,
        response_format={"type": "json_object"},
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": batch_text},
        ],
    )

    content = response.choices[0].message.content or "{}"
    parsed = json_loads_safe(content)
    raw_result = RawTraitImportResult.model_validate(parsed)

    result = AgentTraitImportResult()

    for raw_trait in raw_result.traits:
        valid_trait, invalid_trait = validate_raw_trait(raw_trait)

        if valid_trait:
            result.valid_traits.append(valid_trait)

        if invalid_trait:
            result.invalid_traits.append(invalid_trait)

    return result


def default_worker_count() -> int:
    cpu_count = os.cpu_count() or 4
    return max(1, min(8, cpu_count - 1))


def dedupe_traits(traits: list[TraitImportRow]) -> list[TraitImportRow]:
    seen: set[tuple[str, int]] = set()
    unique: list[TraitImportRow] = []

    for trait in traits:
        key = (trait.trait_name.strip().lower(), trait.lvl)

        if key in seen:
            continue

        seen.add(key)
        unique.append(trait)

    return unique


def extract_traits_with_agent(
    raw_text: str,
    model: str,
    max_batch_chars: int = 12000,
    workers: int | None = None,
) -> tuple[AgentTraitImportResult, dict[str, Any]]:
    blocks = split_trait_blocks(raw_text)
    batches = batch_blocks(blocks, max_batch_chars)

    max_workers = workers or default_worker_count()
    final_result = AgentTraitImportResult()

    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_map = {
            executor.submit(extract_trait_batch_with_agent, batch, model): index
            for index, batch in enumerate(batches)
        }

        for future in as_completed(future_map):
            batch_index = future_map[future]

            try:
                batch_result = future.result()

                final_result.valid_traits.extend(batch_result.valid_traits)
                final_result.invalid_traits.extend(batch_result.invalid_traits)

                print(
                    f"Extracted batch {batch_index + 1}/{len(batches)}: "
                    f"{len(batch_result.valid_traits)} valid, "
                    f"{len(batch_result.invalid_traits)} invalid"
                )

            except Exception as exc:
                final_result.invalid_traits.append(
                    InvalidTraitRow(
                        raw_trait={
                            "batch_index": batch_index + 1,
                            "batch_text_preview": batches[batch_index][:500],
                        },
                        reasons=[f"Batch extraction failed: {exc}"],
                    )
                )

    before_dedupe_count = len(final_result.valid_traits)
    final_result.valid_traits = dedupe_traits(final_result.valid_traits)
    duplicate_count = before_dedupe_count - len(final_result.valid_traits)

    metadata = {
        "total_blocks": len(blocks),
        "total_batches": len(batches),
        "valid_count": len(final_result.valid_traits),
        "invalid_count": len(final_result.invalid_traits),
        "duplicate_count": duplicate_count,
        "max_batch_chars": max_batch_chars,
        "workers": max_workers,
    }

    return final_result, metadata


def normalize_database_url(database_url: str) -> str:
    return database_url.replace("postgresql+asyncpg://", "postgresql://", 1)


async def upsert_traits(
    database_url: str,
    traits: list[TraitImportRow],
) -> list[str]:
    conn = await asyncpg.connect(normalize_database_url(database_url))
    inserted_ids: list[str] = []

    try:
        async with conn.transaction():
            for trait in traits:
                trait_id = await conn.fetchval(
                    """
                    SELECT id
                    FROM traits
                    WHERE lower(trait_name) = lower($1)
                      AND lvl = $2
                    LIMIT 1;
                    """,
                    trait.trait_name,
                    trait.lvl,
                )

                if trait_id:
                    await conn.execute(
                        """
                        UPDATE traits
                        SET trait_name = $2,
                            lvl = $3,
                            purchase_cost = $4,
                            lvl_up_cost = $5,
                            trait_desc = $6,
                            trait_uses = $7,
                            mp_cost = $8,
                            ep_cost = $9,
                            tp_cost = $10,
                            trait_effect = $11,
                            lvl_up_effect = $12,
                            updated_at = NOW()
                        WHERE id = $1;
                        """,
                        trait_id,
                        trait.trait_name,
                        trait.lvl,
                        trait.purchase_cost,
                        trait.lvl_up_cost,
                        trait.trait_desc,
                        trait.trait_uses,
                        trait.mp_cost,
                        trait.ep_cost,
                        trait.tp_cost,
                        trait.trait_effect,
                        trait.lvl_up_effect,
                    )

                    await conn.execute(
                        """
                        DELETE FROM trait_categories
                        WHERE trait_id = $1;
                        """,
                        trait_id,
                    )

                else:
                    trait_id = await conn.fetchval(
                        """
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
                            $1, $2, $3, $4, $5,
                            $6, $7, $8, $9, $10, $11
                        )
                        RETURNING id;
                        """,
                        trait.trait_name,
                        trait.lvl,
                        trait.purchase_cost,
                        trait.lvl_up_cost,
                        trait.trait_desc,
                        trait.trait_uses,
                        trait.mp_cost,
                        trait.ep_cost,
                        trait.tp_cost,
                        trait.trait_effect,
                        trait.lvl_up_effect,
                    )

                for category in trait.categories:
                    await conn.execute(
                        """
                        INSERT INTO trait_categories (
                            trait_id,
                            category
                        )
                        VALUES (
                            $1,
                            $2::trait_category
                        )
                        ON CONFLICT (trait_id, category)
                        DO NOTHING;
                        """,
                        trait_id,
                        category.value,
                    )

                inserted_ids.append(str(trait_id))
                print(f"Imported trait: {trait.trait_name} LVL {trait.lvl}")

        return inserted_ids

    finally:
        await conn.close()


async def insert_valid_traits(
    result: AgentTraitImportResult,
    database_url: str,
) -> list[str]:
    return await upsert_traits(
        database_url=database_url,
        traits=result.valid_traits,
    )


def write_json_output(path: str | Path, result: AgentTraitImportResult) -> None:
    output = {
        "valid_traits": [
            trait.model_dump(mode="json")
            for trait in result.valid_traits
        ],
        "invalid_traits": [
            invalid.model_dump(mode="json")
            for invalid in result.invalid_traits
        ],
    }

    Path(path).write_text(
        json.dumps(output, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )


async def main() -> None:
    parser = argparse.ArgumentParser(description="Import Journey traits into PostgreSQL.")
    parser.add_argument("file", help="Path to the trait text, docx, or pdf file.")
    parser.add_argument("--db-url", default=os.getenv("DATABASE_URL"))
    parser.add_argument("--model", default=os.getenv("OPENAI_MODEL", "gpt-4.1-mini"))
    parser.add_argument("--workers", type=int, default=None)
    parser.add_argument("--batch-chars", type=int, default=12000)
    parser.add_argument("--json-out", default="trait_import_output.json")
    parser.add_argument("--dry-run", action="store_true")

    args = parser.parse_args()

    if not os.getenv("OPENAI_API_KEY"):
        raise RuntimeError("OPENAI_API_KEY is not set.")

    text = extract_text(args.file)

    result, metadata = extract_traits_with_agent(
        raw_text=text,
        model=args.model,
        workers=args.workers,
        max_batch_chars=args.batch_chars,
    )

    write_json_output(args.json_out, result)

    print(f"Wrote extracted traits to {args.json_out}")
    print(json.dumps(metadata, indent=2))

    if args.dry_run:
        print("Dry run complete. No database changes made.")
        return

    if not args.db_url:
        raise RuntimeError("DATABASE_URL is not set. Pass --db-url or set it in your .env.")

    inserted_ids = await insert_valid_traits(result, args.db_url)

    print(f"Imported {len(inserted_ids)} valid trait(s).")


if __name__ == "__main__":
    asyncio.run(main())