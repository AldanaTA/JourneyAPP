# routers/job_routes.py

from __future__ import annotations

import os
from typing import Any

from fastapi import APIRouter, File, HTTPException, Query, UploadFile
from pydantic import BaseModel

from agents.job_importer import (
    AgentJobImportResult,
    extract_jobs_with_agent,
    extract_text,
    insert_valid_jobs,
)
from helpers.upload_helpers import (
    check_source_exists,
    cleanup_temp_file,
    save_upload_to_temp_file,
    validate_upload,
)


router = APIRouter(
    prefix="/api/jobs",
    tags=["Jobs"],
)


class JobImportResponse(BaseModel):
    metadata: dict[str, Any]
    valid_jobs: list[dict[str, Any]]
    invalid_jobs: list[dict[str, Any]]
    new_traits: list[dict[str, Any]]
    invalid_traits: list[dict[str, Any]]
    committed: bool
    inserted_ids: list[str]
    inserted_trait_ids: list[str]


def serialize_job_result(
    result: AgentJobImportResult,
    metadata: dict[str, Any],
    committed: bool = False,
    inserted_ids: list[str] | None = None,
    inserted_trait_ids: list[str] | None = None,
) -> JobImportResponse:
    return JobImportResponse(
        metadata=metadata,
        valid_jobs=[job.model_dump(mode="json") for job in result.valid_jobs],
        invalid_jobs=[invalid.model_dump(mode="json") for invalid in result.invalid_jobs],
        new_traits=[trait.model_dump(mode="json") for trait in result.new_traits],
        invalid_traits=[invalid.model_dump(mode="json") for invalid in result.invalid_traits],
        committed=committed,
        inserted_ids=inserted_ids or [],
        inserted_trait_ids=inserted_trait_ids or [],
    )


@router.post("/import/preview", response_model=JobImportResponse)
async def preview_job_import(
    file: UploadFile = File(...),
    commit: bool = Query(
        default=False,
        description="If true, insert valid jobs (and any new traits) into Postgres after extraction.",
    ),
    allow_partial_commit: bool = Query(
        default=False,
        description="If false, commit is blocked when invalid jobs are found.",
    ),
    create_missing_traits: bool = Query(
        default=True,
        description=(
            "If true, traits that are not in the database yet are parsed from the job text "
            "and created. If false, jobs referencing unknown traits are marked invalid."
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
        description="Source ID to associate with inserted jobs and new traits.",
    ),
) -> JobImportResponse:
    """
    Upload a .txt, .docx, or .pdf file containing jobs.

    Expected format per job:

        Job Name
        Job Description: ...   (or "Description: ...")
        Traits:
        Trait Name
        LVL: ...
        ...

    Default:
    - Extract jobs, link traits that already exist by name
    - Return valid_jobs, invalid_jobs, and new_traits (traits that would be created)
    - Do not insert into the database

    With commit=true:
    - Insert new traits (existing traits are never modified)
    - Insert/update valid jobs with their trait links and categories
    - Block commit if invalid jobs exist unless allow_partial_commit=true
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
        prefix="job_import_",
    )

    try:
        raw_text = extract_text(temp_path)

        if not raw_text.strip():
            raise HTTPException(
                status_code=400,
                detail="No text could be extracted from the uploaded file.",
            )

        selected_model = model or os.getenv("OPENAI_MODEL", "gpt-4.1-mini")

        result, metadata = extract_jobs_with_agent(
            raw_text=raw_text,
            model=selected_model,
            database_url=database_url,
            max_batch_chars=max_batch_chars,
            create_missing_traits=create_missing_traits,
        )

        response_metadata = {
            **metadata,
            "file_name": file.filename,
            "model": selected_model,
        }

        if not commit:
            return serialize_job_result(result=result, metadata=response_metadata)

        if (result.invalid_jobs or result.invalid_traits) and not allow_partial_commit:
            raise HTTPException(
                status_code=400,
                detail=(
                    "Import contains invalid jobs or traits. Review the preview first, "
                    "or use allow_partial_commit=true to insert only valid jobs."
                ),
            )

        inserted = insert_valid_jobs(
            result=result,
            database_url=database_url,
            source_id=source_id,
        )

        return serialize_job_result(
            result=result,
            metadata=response_metadata,
            committed=True,
            inserted_ids=inserted["job_ids"],
            inserted_trait_ids=inserted["trait_ids"],
        )

    finally:
        cleanup_temp_file(temp_path)
