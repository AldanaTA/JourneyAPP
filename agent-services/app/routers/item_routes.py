# routers/item_routes.py

from __future__ import annotations

import os
from typing import Any

from fastapi import APIRouter, File, HTTPException, Query, UploadFile
from pydantic import BaseModel

from agents.item_importer import (
    AgentItemImportResult,
    extract_items,
    extract_text,
    insert_valid_items,
)
from helpers.upload_helpers import (
    check_source_exists,
    cleanup_temp_file,
    save_upload_to_temp_file,
    validate_upload,
)


router = APIRouter(
    prefix="/api/items",
    tags=["Items"],
)


class ItemImportResponse(BaseModel):
    metadata: dict[str, Any]
    valid_items: list[dict[str, Any]]
    invalid_items: list[dict[str, Any]]
    committed: bool
    inserted_ids: list[str]


def serialize_item_result(
    result: AgentItemImportResult,
    metadata: dict[str, Any],
    committed: bool = False,
    inserted_ids: list[str] | None = None,
) -> ItemImportResponse:
    return ItemImportResponse(
        metadata=metadata,
        valid_items=[item.model_dump(mode="json") for item in result.valid_items],
        invalid_items=[invalid.model_dump(mode="json") for invalid in result.invalid_items],
        committed=committed,
        inserted_ids=inserted_ids or [],
    )


@router.post("/import/preview", response_model=ItemImportResponse)
async def preview_item_import(
    file: UploadFile = File(...),
    commit: bool = Query(
        default=False,
        description="If true, insert valid items into Postgres after extraction.",
    ),
    allow_partial_commit: bool = Query(
        default=False,
        description="If false, commit is blocked when invalid items are found.",
    ),
    source_id: str = Query(
        default=None,
        description="Source ID to associate with inserted items.",
    ),
) -> ItemImportResponse:
    """
    Upload a .txt, .docx, or .pdf file containing items.

    Items are parsed from their labelled fields (no AI model is used). Expected format per item:

        Item Name
        Description: ...        (optional)
        Rarity: Common
        Stackable: 3            (optional)
        Effect: ...             (optional)
        Cost: 45 Gold

    Weapons add Weapon Type / Weapon Properties / Range / Damage / Mag; armor and shields add
    Strength Req; magic items add Attunement / Charges; skill books add Skills / Skill Bonus /
    Sessions; gems and metals add Magic Item Effect / Armor Effect / Weapon Effect.

    Default:
    - Extract items and return valid_items (with warnings) and invalid_items
    - Do not insert into the database

    With commit=true:
    - Insert/update valid items, their weapon stats and weapon properties
    - Block commit if invalid items exist unless allow_partial_commit=true
    """
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
        prefix="item_import_",
    )

    try:
        raw_text = extract_text(temp_path)

        if not raw_text.strip():
            raise HTTPException(
                status_code=400,
                detail="No text could be extracted from the uploaded file.",
            )

        result, metadata = extract_items(raw_text=raw_text, database_url=database_url)
        response_metadata = {**metadata, "file_name": file.filename}

        if not commit:
            return serialize_item_result(result=result, metadata=response_metadata)

        if result.invalid_items and not allow_partial_commit:
            raise HTTPException(
                status_code=400,
                detail=(
                    "Import contains invalid items. Review the preview first, "
                    "or use allow_partial_commit=true to insert only valid items."
                ),
            )

        inserted_ids = insert_valid_items(
            result=result,
            database_url=database_url,
            source_id=source_id,
        )

        return serialize_item_result(
            result=result,
            metadata=response_metadata,
            committed=True,
            inserted_ids=inserted_ids,
        )

    finally:
        cleanup_temp_file(temp_path)
