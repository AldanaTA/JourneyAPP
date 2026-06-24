"""
power_import_api.py

FastAPI upload service for `power_import_agent_updated.py`.

Put this file in the same folder as:
    power_import_agent_updated.py

Install extra API dependencies:
    pip install fastapi uvicorn[standard] python-multipart

Run:
    uvicorn agent_endpoints:app --reload --host 0.0.0.0 --port 8000

Preview import:
    POST http://localhost:8000/api/powers/import/preview
    form-data:
        file = your .txt/.docx/.pdf file

Commit import:
    POST http://localhost:8000/api/powers/import/preview?commit=true

Environment:
    OPENAI_API_KEY=your_api_key
    DATABASE_URL=postgresql://user:password@localhost:5432/dbname
    OPENAI_MODEL=gpt-4.1-mini
"""

from __future__ import annotations

import os
import shutil
import tempfile
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from fastapi import FastAPI, File, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from agents.power_importer import (
    AgentImportResult,
    extract_powers_with_agent,
    extract_text,
    insert_valid_powers,
)


load_dotenv()


app = FastAPI(
    title="Power Import API",
    version="1.0.0",
    description="Upload a TXT, DOCX, or PDF file and extract powers into the database schema.",
)


# Adjust this for your frontend URL in production.
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
    ],
    allow_origin_regex=r"^http://192\.168\.40\.\d{1,3}:8800$",
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


ALLOWED_EXTENSIONS = {".txt", ".docx", ".pdf"}
MAX_UPLOAD_MB = int(os.getenv("MAX_UPLOAD_MB", "25"))
MAX_UPLOAD_BYTES = MAX_UPLOAD_MB * 1024 * 1024


class ImportResponse(BaseModel):
    metadata: dict[str, Any]
    valid_powers: list[dict[str, Any]]
    invalid_powers: list[dict[str, Any]]
    committed: bool
    inserted_ids: list[str]


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


def validate_upload(file: UploadFile) -> str:
    if not file.filename:
        raise HTTPException(status_code=400, detail="Uploaded file must have a filename.")

    suffix = Path(file.filename).suffix.lower()

    if suffix not in ALLOWED_EXTENSIONS:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported file type {suffix!r}. Use .txt, .docx, or .pdf.",
        )

    return suffix


async def save_upload_to_temp_file(file: UploadFile, suffix: str) -> Path:
    temp_dir = Path(tempfile.mkdtemp(prefix="power_import_"))
    temp_path = temp_dir / f"uploaded_power_file{suffix}"

    total_size = 0

    try:
        with temp_path.open("wb") as buffer:
            while True:
                chunk = await file.read(1024 * 1024)

                if not chunk:
                    break

                total_size += len(chunk)

                if total_size > MAX_UPLOAD_BYTES:
                    raise HTTPException(
                        status_code=413,
                        detail=f"File too large. Max upload size is {MAX_UPLOAD_MB} MB.",
                    )

                buffer.write(chunk)

        return temp_path

    except Exception:
        shutil.rmtree(temp_dir, ignore_errors=True)
        raise


def cleanup_temp_file(temp_path: Path) -> None:
    shutil.rmtree(temp_path.parent, ignore_errors=True)


def serialize_result(
    result: AgentImportResult,
    metadata: dict[str, Any],
    committed: bool = False,
    inserted_ids: list[str] | None = None,
) -> ImportResponse:
    return ImportResponse(
        metadata=metadata,
        valid_powers=[power.model_dump() for power in result.valid_powers],
        invalid_powers=[power.model_dump() for power in result.invalid_powers],
        committed=committed,
        inserted_ids=inserted_ids or [],
    )


@app.post("/api/powers/import/preview", response_model=ImportResponse)
async def preview_power_import(
    file: UploadFile = File(...),
    commit: bool = Query(
        default=False,
        description="If true, insert valid powers into Postgres after extraction.",
    ),
    allow_partial_commit: bool = Query(
        default=False,
        description="If false, commit is blocked when invalid powers are found.",
    ),
    model: str | None = Query(
        default=None,
        description="Optional OpenAI model override.",
    ),
    max_batch_chars: int = Query(
        default=24000,
        ge=4000,
        le=100000,
        description="Approximate max characters sent to the model per batch.",
    ),
) -> ImportResponse:
    """
    Upload a .txt, .docx, or .pdf file.

    Default behavior:
    - Extract powers
    - Return preview JSON
    - Do not insert into database

    With ?commit=true:
    - Extract powers
    - Insert valid powers into database
    - By default, commit is blocked if there are invalid powers
    """
    if not os.getenv("OPENAI_API_KEY"):
        raise HTTPException(
            status_code=500,
            detail="OPENAI_API_KEY is missing on the server.",
        )

    suffix = validate_upload(file)
    temp_path = await save_upload_to_temp_file(file, suffix)

    try:
        raw_text = extract_text(temp_path)

        if not raw_text.strip():
            raise HTTPException(
                status_code=400,
                detail="No text could be extracted from the uploaded file.",
            )

        selected_model = model or os.getenv("OPENAI_MODEL", "gpt-4.1-mini")

        result, metadata = extract_powers_with_agent(
            raw_text=raw_text,
            model=selected_model,
            max_batch_chars=max_batch_chars,
        )

        if not commit:
            return serialize_result(result, metadata)

        if result.invalid_powers and not allow_partial_commit:
            raise HTTPException(
                status_code=400,
                detail=(
                    "Import contains invalid powers. Review the preview first, "
                    "or use allow_partial_commit=true to insert only valid powers."
                ),
            )

        database_url = os.getenv("DATABASE_URL")

        if not database_url:
            raise HTTPException(
                status_code=500,
                detail="DATABASE_URL is missing on the server.",
            )

        inserted_ids = insert_valid_powers(result, database_url)

        return serialize_result(
            result=result,
            metadata=metadata,
            committed=True,
            inserted_ids=inserted_ids,
        )

    finally:
        cleanup_temp_file(temp_path)
