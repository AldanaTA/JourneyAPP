# routers/race_routes.py

from __future__ import annotations

import os
from typing import Any

from fastapi import APIRouter, File, HTTPException, Query, UploadFile
from pydantic import BaseModel

from agents.race_importer import (
    AgentRaceImportResult,
    extract_races_with_agent,
    extract_text,
    insert_valid_races,
)
from helpers.upload_helpers import (
    check_source_exists,
    cleanup_temp_file,
    save_upload_to_temp_file,
    validate_upload,
)


router = APIRouter(
    prefix="/api/races",
    tags=["Races"],
)


class RaceImportResponse(BaseModel):
    metadata: dict[str, Any]
    valid_races: list[dict[str, Any]]
    invalid_races: list[dict[str, Any]]
    new_traits: list[dict[str, Any]]
    updated_traits: list[dict[str, Any]]
    invalid_traits: list[dict[str, Any]]
    committed: bool
    inserted_ids: list[str]
    inserted_trait_ids: list[str]


def serialize_race_result(
    result: AgentRaceImportResult,
    metadata: dict[str, Any],
    committed: bool = False,
    inserted_ids: list[str] | None = None,
    inserted_trait_ids: list[str] | None = None,
) -> RaceImportResponse:
    return RaceImportResponse(
        metadata=metadata,
        valid_races=[race.model_dump(mode="json") for race in result.valid_races],
        invalid_races=[invalid.model_dump(mode="json") for invalid in result.invalid_races],
        new_traits=[trait.model_dump(mode="json") for trait in result.new_traits],
        updated_traits=[trait.model_dump(mode="json") for trait in result.updated_traits],
        invalid_traits=[invalid.model_dump(mode="json") for invalid in result.invalid_traits],
        committed=committed,
        inserted_ids=inserted_ids or [],
        inserted_trait_ids=inserted_trait_ids or [],
    )


@router.post("/import/preview", response_model=RaceImportResponse)
async def preview_race_import(
    file: UploadFile = File(...),
    commit: bool = Query(
        default=False,
        description="If true, insert valid races (and their racial traits) into Postgres after extraction.",
    ),
    allow_partial_commit: bool = Query(
        default=False,
        description="If false, commit is blocked when invalid races or traits are found.",
    ),
    update_existing_traits: bool = Query(
        default=False,
        description=(
            "If false, traits that already exist are linked unchanged (the preview warns when their "
            "effect differs). If true, existing traits are overwritten with the race's version."
        ),
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
        description="Source ID to associate with inserted races and traits.",
    ),
) -> RaceImportResponse:
    """
    Upload a .txt, .docx, or .pdf file containing races.

    Expected format per race:

        Race Name
        Size: M        (or a range, e.g. "Size: S - M")
        Movement: 30ft
        Languages: Common and Dwarvish
        Description:
        ...
        Traits:
        Trait Name
        LVL: ...
        ...

    Default:
    - Extract races and their traits; link traits that already exist by name
    - Return valid_races, invalid_races, new_traits, and updated_traits
    - Do not insert into the database

    With commit=true:
    - Insert new traits (tagged "racial"); overwrite existing ones only with update_existing_traits=true
    - Insert/update valid races with their trait links
    - Block commit if invalid races or traits exist unless allow_partial_commit=true
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
        prefix="race_import_",
    )

    try:
        raw_text = extract_text(temp_path)

        if not raw_text.strip():
            raise HTTPException(
                status_code=400,
                detail="No text could be extracted from the uploaded file.",
            )

        selected_model = model or os.getenv("OPENAI_MODEL", "gpt-4.1-mini")

        result, metadata = extract_races_with_agent(
            raw_text=raw_text,
            model=selected_model,
            database_url=database_url,
            max_batch_chars=max_batch_chars,
            update_existing_traits=update_existing_traits,
        )

        response_metadata = {
            **metadata,
            "file_name": file.filename,
            "model": selected_model,
        }

        if not commit:
            return serialize_race_result(result=result, metadata=response_metadata)

        if (result.invalid_races or result.invalid_traits) and not allow_partial_commit:
            raise HTTPException(
                status_code=400,
                detail=(
                    "Import contains invalid races or traits. Review the preview first, "
                    "or use allow_partial_commit=true to insert only valid races."
                ),
            )

        inserted = insert_valid_races(
            result=result,
            database_url=database_url,
            source_id=source_id,
        )

        return serialize_race_result(
            result=result,
            metadata=response_metadata,
            committed=True,
            inserted_ids=inserted["race_ids"],
            inserted_trait_ids=inserted["trait_ids"],
        )

    finally:
        cleanup_temp_file(temp_path)
