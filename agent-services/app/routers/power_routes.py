# routers/power_routes.py

from __future__ import annotations

import os
from typing import Any

from fastapi import APIRouter, File, HTTPException, Query, UploadFile
from pydantic import BaseModel

from agents.power_importer import (
    AgentImportResult,
    extract_powers_with_agent,
    extract_text,
    insert_valid_powers,
)
from upload_helpers import (
    cleanup_temp_file,
    save_upload_to_temp_file,
    validate_upload,
)


router = APIRouter(
    prefix="/api/powers",
    tags=["Powers"],
)


class PowerImportResponse(BaseModel):
    metadata: dict[str, Any]
    valid_powers: list[dict[str, Any]]
    invalid_powers: list[dict[str, Any]]
    committed: bool
    inserted_ids: list[str]


def serialize_power_result(
    result: AgentImportResult,
    metadata: dict[str, Any],
    committed: bool = False,
    inserted_ids: list[str] | None = None,
) -> PowerImportResponse:
    return PowerImportResponse(
        metadata=metadata,
        valid_powers=[power.model_dump() for power in result.valid_powers],
        invalid_powers=[power.model_dump() for power in result.invalid_powers],
        committed=committed,
        inserted_ids=inserted_ids or [],
    )


@router.post("/import/preview", response_model=PowerImportResponse)
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
) -> PowerImportResponse:
    if not os.getenv("OPENAI_API_KEY"):
        raise HTTPException(
            status_code=500,
            detail="OPENAI_API_KEY is missing on the server.",
        )

    suffix = validate_upload(file)
    temp_path = await save_upload_to_temp_file(
        file=file,
        suffix=suffix,
        prefix="power_import_",
    )

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
            return serialize_power_result(result, metadata)

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

        return serialize_power_result(
            result=result,
            metadata=metadata,
            committed=True,
            inserted_ids=inserted_ids,
        )

    finally:
        cleanup_temp_file(temp_path)