# routers/creature_routes.py

from __future__ import annotations

import os
from typing import Any

from fastapi import APIRouter, File, HTTPException, Query, UploadFile
from pydantic import BaseModel

from pathlib import Path

from agents.creature_importer import (
    AgentCreatureImportResult,
    extract_creatures,
    extract_creatures_from_archive,
    extract_text,
    insert_valid_creatures,
)
from helpers.archive_helpers import ArchiveError
from helpers.upload_helpers import (
    check_source_exists,
    cleanup_temp_file,
    save_upload_to_temp_file,
    validate_upload,
)


router = APIRouter(
    prefix="/api/creatures",
    tags=["Creatures"],
)


class CreatureImportResponse(BaseModel):
    metadata: dict[str, Any]
    valid_creatures: list[dict[str, Any]]
    invalid_creatures: list[dict[str, Any]]
    committed: bool
    inserted_ids: list[str]


def serialize_creature_result(
    result: AgentCreatureImportResult,
    metadata: dict[str, Any],
    committed: bool = False,
    inserted_ids: list[str] | None = None,
) -> CreatureImportResponse:
    return CreatureImportResponse(
        metadata=metadata,
        valid_creatures=[creature.model_dump(mode="json") for creature in result.valid_creatures],
        invalid_creatures=[invalid.model_dump(mode="json") for invalid in result.invalid_creatures],
        committed=committed,
        inserted_ids=inserted_ids or [],
    )


@router.post("/import/preview", response_model=CreatureImportResponse)
async def preview_creature_import(
    file: UploadFile = File(...),
    commit: bool = Query(
        default=False,
        description="If true, insert valid creatures into Postgres after extraction.",
    ),
    allow_partial_commit: bool = Query(
        default=False,
        description="If false, commit is blocked when invalid creatures are found.",
    ),
    source_id: str = Query(
        default=None,
        description="Source ID to associate with inserted creatures (creature types are looked up within this source).",
    ),
) -> CreatureImportResponse:
    """
    Upload a .txt, .docx, or .pdf file containing creature stat blocks.

    Stat blocks are parsed from their labels (no AI model is used), following the template in the
    Storyteller Tools (Creature Creation):

        Name: ...
        Threat LVL: 7  Type: Humanoid  Size: S-M
        Description: ...
        Languages: ...
        Lore
        ...
        Combat
        HP: 125  WP: 0  MP: 0  EP: 0
        PRIO: 5  MVM: 30ft  PD: 12  MD: 0
        Skills: Brawl 140%, Dodge 100%
        Augments
        Immune: ...   Resistant: ...   Weak: ...
        Abilities
        Passives: / Triggers: / Declared:
        Ability Name: effect            (optional "Cost: 1TP" / "Uses: 3" lines after it)
        Bite: Brawl, Range: 5ft, Crusher, Damage: 1d8 + 12 slashing      (an attack)
        Darkness: The creature casts Darkness at LVL 7. 31MP 3TP        (a power cast)

    Abilities whose names match a game trait or power are linked to it; near misses get a
    "did you mean ...?" warning. Unknown creature types (per source), damage types, and weapon
    properties make a creature invalid.

    Default:
    - Extract creatures and return valid_creatures (with warnings) and invalid_creatures
    - Do not insert into the database

    With commit=true:
    - Insert/update valid creatures, replacing their augments, abilities, and attacks
    - Block commit if invalid creatures exist unless allow_partial_commit=true
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
        prefix="creature_import_",
    )

    try:
        raw_text = extract_text(temp_path)

        if not raw_text.strip():
            raise HTTPException(
                status_code=400,
                detail="No text could be extracted from the uploaded file.",
            )

        result, metadata = extract_creatures(raw_text=raw_text, database_url=database_url, source_id=source_id)
        response_metadata = {**metadata, "file_name": file.filename}

        if not commit:
            return serialize_creature_result(result=result, metadata=response_metadata)

        if result.invalid_creatures and not allow_partial_commit:
            raise HTTPException(
                status_code=400,
                detail=(
                    "Import contains invalid creatures. Review the preview first, "
                    "or use allow_partial_commit=true to insert only valid creatures."
                ),
            )

        inserted_ids = insert_valid_creatures(
            result=result,
            database_url=database_url,
            source_id=source_id,
        )

        return serialize_creature_result(
            result=result,
            metadata=response_metadata,
            committed=True,
            inserted_ids=inserted_ids,
        )

    finally:
        cleanup_temp_file(temp_path)



class CreatureArchiveImportResponse(CreatureImportResponse):
    # One entry per .docx file: {"file": path in the zip, "creatures": [names], "invalid": n, "error": ...}
    files: list[dict[str, Any]]


@router.post("/import/archive", response_model=CreatureArchiveImportResponse)
async def import_creature_archive(
    file: UploadFile = File(...),
    commit: bool = Query(
        default=False,
        description="If true, insert valid creatures into Postgres after extraction.",
    ),
    allow_partial_commit: bool = Query(
        default=False,
        description="If false, commit is blocked when any file has invalid creatures.",
    ),
    source_id: str = Query(
        default=None,
        description="Source ID to associate with inserted creatures (creature types are looked up within this source).",
    ),
) -> CreatureArchiveImportResponse:
    """
    Upload a .zip of creature stat blocks (.docx), for example:

        Main Dir/
          Threat Level 1/
            Goblin.docx
            Wolf.docx
          Threat Level 2/
            ...

    Every .docx file anywhere in the zip is run through the creature importer (other files and OS
    clutter such as __MACOSX/ are ignored). The response lists each file with the creatures found in
    it. Creatures whose Threat LVL differs from their "Threat Level N" folder get a warning, and a
    creature name that appears in more than one file is imported once (from the first file).

    Default: preview only. With commit=true: insert/update all valid creatures in one transaction,
    blocked if any creature is invalid unless allow_partial_commit=true.
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

    if not file.filename or Path(file.filename).suffix.lower() != ".zip":
        raise HTTPException(
            status_code=400,
            detail="Upload a .zip archive of creature .docx files.",
        )

    check_source_exists(source_id, database_url)

    temp_path = await save_upload_to_temp_file(
        file=file,
        suffix=".zip",
        prefix="creature_archive_",
    )

    try:
        try:
            result, files, metadata = extract_creatures_from_archive(
                zip_path=str(temp_path),
                database_url=database_url,
                source_id=source_id,
            )
        except ArchiveError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc

        if not files:
            raise HTTPException(
                status_code=400,
                detail="The archive contains no .docx files.",
            )

        response_metadata = {**metadata, "file_name": file.filename}
        inserted_ids: list[str] = []

        if commit:
            if result.invalid_creatures and not allow_partial_commit:
                raise HTTPException(
                    status_code=400,
                    detail=(
                        "Archive contains invalid creatures. Review the preview first, "
                        "or use allow_partial_commit=true to insert only valid creatures."
                    ),
                )
            inserted_ids = insert_valid_creatures(
                result=result,
                database_url=database_url,
                source_id=source_id,
            )

        base = serialize_creature_result(
            result=result,
            metadata=response_metadata,
            committed=commit,
            inserted_ids=inserted_ids,
        )
        return CreatureArchiveImportResponse(**base.model_dump(), files=files)

    finally:
        cleanup_temp_file(temp_path)
