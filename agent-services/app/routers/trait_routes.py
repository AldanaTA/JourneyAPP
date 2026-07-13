# routers/trait_routes.py

from __future__ import annotations

import os
from typing import Any

from fastapi import APIRouter, File, HTTPException, Query, UploadFile
from pydantic import BaseModel

from agents.trait_importer import (
    AgentTraitImportResult,
    extract_text,
    extract_traits_with_agent,
    insert_valid_traits,
)

from helpers.upload_helpers import (
    check_source_exists,
    cleanup_temp_file,
    save_upload_to_temp_file,
    validate_upload,
)


router = APIRouter(
    prefix="/api/traits",
    tags=["Traits"],
)


class TraitImportResponse(BaseModel):
    metadata: dict[str, Any]
    valid_traits: list[dict[str, Any]]
    invalid_traits: list[dict[str, Any]]
    committed: bool
    inserted_ids: list[str]


def serialize_trait_result(
    result: AgentTraitImportResult,
    metadata: dict[str, Any],
    committed: bool = False,
    inserted_ids: list[str] | None = None,
) -> TraitImportResponse:
    return TraitImportResponse(
        metadata=metadata,
        valid_traits=[
            trait.model_dump(mode="json")
            for trait in result.valid_traits
        ],
        invalid_traits=[
            invalid.model_dump(mode="json")
            for invalid in result.invalid_traits
        ],
        committed=committed,
        inserted_ids=inserted_ids or [],
    )


@router.post("/import/preview", response_model=TraitImportResponse)
async def preview_trait_import(
    file: UploadFile = File(...),
    commit: bool = Query(
        default=False,
        description="If true, insert valid traits into Postgres after extraction.",
    ),
    allow_partial_commit: bool = Query(
        default=False,
        description="If false, commit is blocked when invalid traits are found.",
    ),
    model: str | None = Query(
        default=None,
        description="Optional OpenAI model override.",
    ),
    max_batch_chars: int = Query(
        default=12000,
        ge=4000,
        le=100000,
        description="Approximate max characters sent to the model per batch.",
    ),
    source_id: str = Query(
        default=None,
        description="Source ID to associate with inserted traits.",
    ),
) -> TraitImportResponse:
    """
    Upload a .txt, .docx, or .pdf file.

    Default:
    - Extract traits
    - Return valid_traits and invalid_traits
    - Do not insert into the database

    With commit=true:
    - Insert only valid traits
    - Block commit if invalid traits exist unless allow_partial_commit=true
    """
    if not os.getenv("OPENAI_API_KEY"):
        raise HTTPException(
            status_code=500,
            detail="OPENAI_API_KEY is missing on the server.",
        )
    database_url = os.getenv("DATABASE_URL")

    if not database_url:
        raise HTTPException(
              status_code=500,
              detail="DATABASE_URL is missing on the server.",
        )
    if source_id is None:
        raise HTTPException(
            status_code=400,
            detail="source_id query parameter is required.",
        )
    check_source_exists(source_id, database_url)
    suffix = validate_upload(file)

    temp_path = await save_upload_to_temp_file(
        file=file,
        suffix=suffix,
        prefix="trait_import_",
    )

    try:
        raw_text = extract_text(temp_path)

        if not raw_text.strip():
            raise HTTPException(
                status_code=400,
                detail="No text could be extracted from the uploaded file.",
            )

        selected_model = model or os.getenv("OPENAI_MODEL", "gpt-4.1-mini")

        result, metadata = extract_traits_with_agent(
            raw_text=raw_text,
            model=selected_model,
            max_batch_chars=max_batch_chars,
        )

        response_metadata = {
            **metadata,
            "file_name": file.filename,
            "model": selected_model,
        }

        if not commit:
            return serialize_trait_result(
                result=result,
                metadata=response_metadata,
            )

        if result.invalid_traits and not allow_partial_commit:
            raise HTTPException(
                status_code=400,
                detail=(
                    "Import contains invalid traits. Review the preview first, "
                    "or use allow_partial_commit=true to insert only valid traits."
                ),
            )

        inserted_ids = insert_valid_traits(
            result=result,
            database_url=database_url,
            source_id=source_id,
        )

        return serialize_trait_result(
            result=result,
            metadata=response_metadata,
            committed=True,
            inserted_ids=inserted_ids,
        )

    finally:
        cleanup_temp_file(temp_path)