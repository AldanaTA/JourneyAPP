# helpers/upload_helpers.py

from __future__ import annotations

import os
import shutil
import tempfile
from pathlib import Path

from fastapi import HTTPException, UploadFile


ALLOWED_EXTENSIONS = {".txt", ".docx", ".pdf"}
MAX_UPLOAD_MB = int(os.getenv("MAX_UPLOAD_MB", "25"))
MAX_UPLOAD_BYTES = MAX_UPLOAD_MB * 1024 * 1024


def validate_upload(file: UploadFile) -> str:
    if not file.filename:
        raise HTTPException(
            status_code=400,
            detail="Uploaded file must have a filename.",
        )

    suffix = Path(file.filename).suffix.lower()

    if suffix not in ALLOWED_EXTENSIONS:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported file type {suffix!r}. Use .txt, .docx, or .pdf.",
        )

    return suffix


async def save_upload_to_temp_file(
    file: UploadFile,
    suffix: str,
    prefix: str,
) -> Path:
    temp_dir = Path(tempfile.mkdtemp(prefix=prefix))
    temp_path = temp_dir / f"uploaded_file{suffix}"

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
def check_source_exists(source_id: str, database_url: str) -> None:
    import psycopg

    try:
        conn = psycopg.connect(database_url)
        cursor = conn.cursor()
        cursor.execute("SELECT 1 FROM content_sources WHERE id = %s", (source_id,))
        if cursor.fetchone() is None:
            raise HTTPException(
                status_code=400,
                detail=f"Content source with ID {source_id} does not exist.",
            )
    except psycopg.Error as e:
        raise HTTPException(
            status_code=500,
            detail=f"Database error: {e}",
        )
    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()
            
def cleanup_temp_file(temp_path: Path) -> None:
    shutil.rmtree(temp_path.parent, ignore_errors=True)