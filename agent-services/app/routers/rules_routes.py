# routers/rules_routes.py

from __future__ import annotations

import os
from pathlib import Path
from typing import Any

from fastapi import APIRouter, File, HTTPException, Query, UploadFile
from pydantic import BaseModel

from agents.rules_importer import (
    RulesImportResult,
    chapter_from_filename,
    extract_rules_from_archive,
    extract_rules_from_docx,
    fetch_entity_index,
    insert_rule_chapters,
)
from helpers.archive_helpers import ArchiveError
from helpers.upload_helpers import (
    check_source_exists,
    cleanup_temp_file,
    save_upload_to_temp_file,
)


router = APIRouter(
    prefix="/api/rules",
    tags=["Rules"],
)


class RulesImportResponse(BaseModel):
    # Per chapter: file, source, title, number, section/card/image counts, missing entities, warnings,
    # and (with include_sections=true) the sections themselves.
    chapters: list[dict[str, Any]]
    skipped_files: list[dict[str, Any]]
    errors: list[dict[str, Any]]
    committed: bool
    stats: dict[str, Any] | None = None


def serialize(result: RulesImportResult, include_sections: bool, committed: bool, stats: dict[str, Any] | None) -> RulesImportResponse:
    chapters = []
    for chapter in result.chapters:
        summary = {
            "file": chapter.file,
            "source_name": chapter.source_name,
            "title": chapter.title,
            "chapter_key": chapter.chapter_key,
            "number": chapter.number,
            "section_count": len(chapter.sections),
            "card_count": sum(len(section.entities) for section in chapter.sections),
            "image_count": len(chapter.assets),
            "missing_entities": [missing.model_dump() for missing in chapter.missing_entities],
            "warnings": chapter.warnings,
        }
        if include_sections:
            summary["sections"] = [section.model_dump() for section in chapter.sections]
            summary["assets"] = [asset.model_dump() for asset in chapter.assets]
        chapters.append(summary)

    return RulesImportResponse(
        chapters=chapters,
        skipped_files=[skipped.model_dump() for skipped in result.skipped_files],
        errors=[error.model_dump() for error in result.errors],
        committed=committed,
        stats=stats,
    )


def required_database_url() -> str:
    database_url = os.getenv("DATABASE_URL")
    if not database_url:
        raise HTTPException(status_code=500, detail="DATABASE_URL is missing on the server.")
    return database_url


def commit_result(result: RulesImportResult, database_url: str, allow_partial_commit: bool) -> dict[str, Any]:
    if result.errors and not allow_partial_commit:
        raise HTTPException(
            status_code=400,
            detail="Some files could not be imported. Review the preview first, or use allow_partial_commit=true.",
        )
    try:
        return insert_rule_chapters(result.chapters, database_url, os.getenv("ASSET_STORAGE_DIR"))
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/import/archive", response_model=RulesImportResponse)
async def import_rules_archive(
    file: UploadFile = File(...),
    commit: bool = Query(default=False, description="If true, write the chapters to the database."),
    allow_partial_commit: bool = Query(default=False, description="If false, commit is blocked when any file failed."),
    include_sections: bool = Query(default=False, description="Include every section (with its Markdown) in the response."),
) -> RulesImportResponse:
    """
    Upload a .zip of the rules folder, for example:

        Rules/
          01_Basic Rules.docx                    -> Core
          04_Combat(Journey 100).docx            -> Core
          Settings/Calthora Setting/
            02_World_Lore.docx                   -> Calthora

    Only numbered chapter files are imported. "00_" files (the whole book), unnumbered files, the
    Bestiary, and "Creatures/" folders are skipped and listed in skipped_files. Files under
    "Settings/<Name> Setting/" go to the content source named <Name>; everything else goes to Core.

    Word headings become sections, text becomes Markdown, embedded images become assets, and entity
    blocks that already exist in the database (traits, powers, items, races, jobs, creatures) become
    inline card references. Entity blocks not in the database keep their text and are listed under
    missing_entities, so the dedicated importer can be run first.

    Default: preview only. With commit=true: chapters are upserted (sections matched by slug; changed
    sections get a revision), images are written to ASSET_STORAGE_DIR, and chapters are added to their
    source's book in number order.
    """
    database_url = required_database_url()
    if not file.filename or Path(file.filename).suffix.lower() != ".zip":
        raise HTTPException(status_code=400, detail="Upload a .zip archive of the rules folder.")

    temp_path = await save_upload_to_temp_file(file=file, suffix=".zip", prefix="rules_archive_")
    try:
        try:
            result = extract_rules_from_archive(str(temp_path), database_url)
        except ArchiveError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        if not result.chapters and not result.errors:
            raise HTTPException(status_code=400, detail="The archive contains no numbered chapter files.")

        stats = commit_result(result, database_url, allow_partial_commit) if commit else None
        return serialize(result, include_sections, committed=commit, stats=stats)
    finally:
        cleanup_temp_file(temp_path)


@router.post("/import/preview", response_model=RulesImportResponse)
async def import_rules_chapter(
    file: UploadFile = File(...),
    source_id: str = Query(default=None, description="Content source the chapter belongs to."),
    commit: bool = Query(default=False, description="If true, write the chapter to the database."),
    include_sections: bool = Query(default=True, description="Include every section (with its Markdown) in the response."),
) -> RulesImportResponse:
    """
    Upload one numbered chapter (.docx), e.g. "04_Combat(Journey 100).docx", to (re-)import just that
    chapter into the given source. Works like the archive import otherwise.
    """
    database_url = required_database_url()
    if source_id is None:
        raise HTTPException(status_code=400, detail="source_id query parameter is required.")
    check_source_exists(source_id, database_url)

    if not file.filename or Path(file.filename).suffix.lower() != ".docx":
        raise HTTPException(status_code=400, detail="Upload a .docx chapter file.")
    parsed = chapter_from_filename(file.filename)
    if parsed is None:
        raise HTTPException(status_code=400, detail="The file name must start with a chapter number, e.g. '04_Combat.docx'.")
    number, title, chapter_key = parsed

    import psycopg
    from agents.trait_importer import normalize_database_url

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        source_name = conn.execute(
            "SELECT source_name FROM content_sources WHERE id = %s;", (source_id,)
        ).fetchone()[0]
    index = fetch_entity_index(database_url)

    temp_path = await save_upload_to_temp_file(file=file, suffix=".docx", prefix="rules_chapter_")
    try:
        chapter = extract_rules_from_docx(temp_path, file.filename, source_name, number, title, chapter_key, index)
        result = RulesImportResult(chapters=[chapter], skipped_files=[])
        stats = commit_result(result, database_url, allow_partial_commit=False) if commit else None
        return serialize(result, include_sections, committed=commit, stats=stats)
    finally:
        cleanup_temp_file(temp_path)
