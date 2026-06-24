"""
power_import_agent.py

Starter import agent for your `powers` Postgres table.

Supports:
- .txt
- .docx
- .pdf

Flow:
1. Read file text.
2. Ask OpenAI to extract powers into a strict JSON schema.
3. Validate rows locally with Pydantic.
4. Save an import preview JSON.
5. Optionally insert valid powers into Postgres with --commit.

Install:
    pip install openai python-dotenv pydantic psycopg[binary] pypdf python-docx

Environment:
    OPENAI_API_KEY=your_api_key
    DATABASE_URL=postgresql://user:password@localhost:5432/dbname

Examples:
    python power_import_agent.py ./powers.txt --out import_preview.json
    python power_import_agent.py ./powers.docx --out import_preview.json
    python power_import_agent.py ./powers.pdf --out import_preview.json

Commit to database:
    python power_import_agent.py ./powers.txt --commit
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any, Literal

from dotenv import load_dotenv
from openai import OpenAI
from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator


# ----------------------------
# Your DB enum
# ----------------------------

PowerType = Literal["Destruction", "Support", "Sabotage", "Utility"]


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
        if text == "" or text.lower() in {"none", "n/a", "null"}:
            return None
        return text

    @field_validator("material_components", mode="after")
    @classmethod
    def default_material_components(cls, value: str) -> str:
        return value if value else "None"

    @field_validator("range", mode="after")
    @classmethod
    def default_range(cls, value: str) -> str:
        return value if value else "5"

    @field_validator("area", mode="after")
    @classmethod
    def default_area(cls, value: str) -> str:
        return value if value else "0"

    @field_validator("duration", mode="after")
    @classmethod
    def default_duration(cls, value: str) -> str:
        return value if value else "Instantaneous"


class InvalidPower(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source_text: str
    errors: list[str]


class AgentImportResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    valid_powers: list[PowerRow]
    invalid_powers: list[InvalidPower]


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
                },
                # Strict mode works best when every key is present.
                # Nullable fields should be present with null when missing.
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


AGENT_INSTRUCTIONS = """
You are a supervised data import agent for a tabletop power database.

Your job:
- Read the user's document text.
- Identify every power.
- Convert each power into the exact database shape.
- Return only JSON matching the provided schema.

Database rules:
- type must be one of: Destruction, Support, Sabotage, Utility.
- lvl must be an integer.
- tp_cost must be an integer. If missing, infer 0 only when the source clearly implies no TP cost. Otherwise mark invalid.
- hp_cost, mp_cost, ep_cost may be null.
- material_components defaults to "None" if missing.
- range defaults to "5" if missing.
- area defaults to "0" if missing.
- duration defaults to "Instantaneous" if missing.
- verbal, sight, somatic, is_distinct, and concentration must be booleans.
- Convert Yes/No, True/False, Required/Not Required into booleans.
- effect is required.
- empower_effect and lvl_up_effect may be null.

Important:
- Do not invent missing core data like name, lvl, type, required booleans, or effect.
- If a power is too ambiguous, put it in invalid_powers with source_text and errors.
- Preserve rules text in effect fields as much as possible.
"""


# ----------------------------
# File text extraction
# ----------------------------

def read_txt(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def read_docx(path: Path) -> str:
    try:
        from docx import Document
    except ImportError as exc:
        raise RuntimeError("Missing dependency. Install with: pip install python-docx") from exc

    doc = Document(path)
    chunks: list[str] = []

    for paragraph in doc.paragraphs:
        text = paragraph.text.strip()
        if text:
            chunks.append(text)

    # Also read tables, because powers are often formatted as tables.
    for table in doc.tables:
        for row in table.rows:
            cells = [cell.text.strip() for cell in row.cells]
            cells = [cell for cell in cells if cell]
            if cells:
                chunks.append(" | ".join(cells))

    return "\n".join(chunks)


def read_pdf(path: Path) -> str:
    try:
        from pypdf import PdfReader
    except ImportError as exc:
        raise RuntimeError("Missing dependency. Install with: pip install pypdf") from exc

    reader = PdfReader(str(path))
    pages: list[str] = []

    for i, page in enumerate(reader.pages, start=1):
        page_text = page.extract_text() or ""
        pages.append(f"\n--- PAGE {i} ---\n{page_text}")

    return "\n".join(pages)


def extract_text(path: Path) -> str:
    suffix = path.suffix.lower()

    if suffix == ".txt":
        return read_txt(path)

    if suffix == ".docx":
        return read_docx(path)

    if suffix == ".pdf":
        return read_pdf(path)

    raise ValueError(f"Unsupported file type: {suffix}. Use .txt, .docx, or .pdf.")


# ----------------------------
# Agent call
# ----------------------------

def extract_powers_with_agent(raw_text: str, model: str) -> AgentImportResult:
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
                            "Extract powers from the following document text.\n\n"
                            "DOCUMENT TEXT:\n"
                            f"{raw_text}"
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

    # Local validation is still important even with structured outputs.
    return AgentImportResult.model_validate(parsed)


# ----------------------------
# Import preview output
# ----------------------------

def write_preview(result: AgentImportResult, output_path: Path) -> None:
    output_path.write_text(
        json.dumps(result.model_dump(), indent=2, ensure_ascii=False),
        encoding="utf-8",
    )


# ----------------------------
# Database insert
# ----------------------------

INSERT_POWER_SQL = """
INSERT INTO powers (
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
RETURNING id;
"""


def insert_valid_powers(result: AgentImportResult, database_url: str) -> list[str]:
    try:
        import psycopg
    except ImportError as exc:
        raise RuntimeError("Missing dependency. Install with: pip install psycopg[binary]") from exc

    inserted_ids: list[str] = []

    with psycopg.connect(database_url) as conn:
        with conn.cursor() as cur:
            for power in result.valid_powers:
                cur.execute(INSERT_POWER_SQL, power.model_dump())
                inserted_id = cur.fetchone()[0]
                inserted_ids.append(str(inserted_id))

        conn.commit()

    return inserted_ids


# ----------------------------
# Main CLI
# ----------------------------

def main() -> None:
    load_dotenv()

    parser = argparse.ArgumentParser(description="Import powers from a TXT, DOCX, or PDF file.")
    parser.add_argument("file", help="Path to .txt, .docx, or .pdf file.")
    parser.add_argument(
        "--model",
        default="gpt-5.5",
        help="OpenAI model to use. Default: gpt-5.5",
    )
    parser.add_argument(
        "--out",
        default="power_import_preview.json",
        help="Where to save the reviewable JSON preview.",
    )
    parser.add_argument(
        "--commit",
        action="store_true",
        help="Insert valid powers into Postgres after preview generation.",
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

    print("Extracting powers with agent...")
    try:
        result = extract_powers_with_agent(raw_text, model=args.model)
    except ValidationError as exc:
        print("The agent returned JSON, but it failed local validation.")
        raise exc

    write_preview(result, output_path)

    print()
    print("Import preview created.")
    print(f"Valid powers:   {len(result.valid_powers)}")
    print(f"Invalid powers: {len(result.invalid_powers)}")
    print(f"Preview file:   {output_path}")

    if result.invalid_powers:
        print()
        print("Invalid powers found. Review the preview before committing.")

    if args.commit:
        database_url = os.getenv("DATABASE_URL")
        if not database_url:
            raise RuntimeError("DATABASE_URL is missing. Required when using --commit.")

        print()
        print("Committing valid powers to database...")
        inserted_ids = insert_valid_powers(result, database_url)
        print(f"Inserted {len(inserted_ids)} powers.")


if __name__ == "__main__":
    main()
