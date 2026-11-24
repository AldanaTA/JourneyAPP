"""
Reusable helpers for working with uploaded archives (currently .zip).

Uses only the standard library (zipfile). Extraction is defensive because archives are uploaded by
users:
- entries that would land outside the target folder ("../evil", "/etc/x") are rejected
- symbolic links are skipped
- the number of files and the total/per-file uncompressed size are capped (zip bombs)
- OS clutter is skipped: __MACOSX/, ._ resource forks, .DS_Store, Thumbs.db, ~$ Office lock files

Typical use:

    with extracted_zip(zip_path, extensions={".docx"}) as root:
        for path in find_files(root, {".docx"}):
            ...
"""

from __future__ import annotations

import os
import re
import shutil
import stat
import tempfile
import zipfile
from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any

MAX_ARCHIVE_FILES = int(os.getenv("MAX_ARCHIVE_FILES", "2000"))
MAX_ARCHIVE_TOTAL_MB = int(os.getenv("MAX_ARCHIVE_TOTAL_MB", "200"))
MAX_ARCHIVE_FILE_MB = int(os.getenv("MAX_ARCHIVE_FILE_MB", "25"))

IGNORED_NAMES = {".ds_store", "thumbs.db", "desktop.ini"}


class ArchiveError(ValueError):
    """Raised when an archive is invalid, unsafe, or too large."""


@dataclass(frozen=True)
class ArchiveLimits:
    max_files: int = MAX_ARCHIVE_FILES
    max_total_bytes: int = MAX_ARCHIVE_TOTAL_MB * 1024 * 1024
    max_file_bytes: int = MAX_ARCHIVE_FILE_MB * 1024 * 1024


def is_ignored_entry(name: str) -> bool:
    """True for OS/editor clutter that is never real content."""
    parts = PurePosixPath(name).parts
    if any(part == "__MACOSX" for part in parts):
        return True
    filename = parts[-1] if parts else ""
    return (
        filename.lower() in IGNORED_NAMES
        or filename.startswith("._")      # macOS resource forks
        or filename.startswith("~$")      # Office lock files
    )


def _is_symlink(info: zipfile.ZipInfo) -> bool:
    return stat.S_ISLNK(info.external_attr >> 16)


def _safe_target(root: Path, name: str) -> Path:
    """Resolve an entry name inside root, rejecting anything that would escape it."""
    if name.startswith(("/", "\\")) or (len(name) > 1 and name[1] == ":"):
        raise ArchiveError(f"Archive entry has an absolute path: {name!r}")
    target = (root / name).resolve()
    if not target.is_relative_to(root.resolve()):
        raise ArchiveError(f"Archive entry points outside the archive: {name!r}")
    return target


def safe_extract_zip(
    zip_path: Path | str,
    destination: Path | str,
    extensions: set[str] | None = None,
    limits: ArchiveLimits = ArchiveLimits(),
) -> list[Path]:
    """
    Extract a zip into destination and return the extracted file paths (sorted).

    extensions: when given (e.g. {".docx"}), only files with these suffixes are extracted, which
    keeps unrelated content (images, spreadsheets) off disk.
    """
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    wanted = {ext.lower() for ext in extensions} if extensions else None

    try:
        archive = zipfile.ZipFile(zip_path)
    except zipfile.BadZipFile as exc:
        raise ArchiveError("The uploaded file is not a valid zip archive.") from exc

    with archive:
        entries = [
            info for info in archive.infolist()
            if not info.is_dir()
            and not is_ignored_entry(info.filename)
            and not _is_symlink(info)
            and (wanted is None or PurePosixPath(info.filename).suffix.lower() in wanted)
        ]

        if len(entries) > limits.max_files:
            raise ArchiveError(f"Archive has {len(entries)} files; the limit is {limits.max_files}.")

        total = sum(info.file_size for info in entries)
        if total > limits.max_total_bytes:
            raise ArchiveError(
                f"Archive unpacks to {total // (1024 * 1024)} MB; the limit is "
                f"{limits.max_total_bytes // (1024 * 1024)} MB."
            )

        extracted: list[Path] = []
        for info in entries:
            if info.file_size > limits.max_file_bytes:
                raise ArchiveError(
                    f"{info.filename!r} unpacks to {info.file_size // (1024 * 1024)} MB; the limit is "
                    f"{limits.max_file_bytes // (1024 * 1024)} MB."
                )

            target = _safe_target(destination, info.filename)
            target.parent.mkdir(parents=True, exist_ok=True)

            # Stream with a hard byte cap in case the header under-reports the size.
            written = 0
            with archive.open(info) as source, target.open("wb") as out:
                while chunk := source.read(1024 * 1024):
                    written += len(chunk)
                    if written > limits.max_file_bytes:
                        raise ArchiveError(f"{info.filename!r} is larger than its header claims.")
                    out.write(chunk)

            extracted.append(target)

    return sorted(extracted)


@contextmanager
def extracted_zip(
    zip_path: Path | str,
    extensions: set[str] | None = None,
    limits: ArchiveLimits = ArchiveLimits(),
) -> Iterator[Path]:
    """Extract a zip into a temporary folder, yield that folder, and delete it afterwards."""
    root = Path(tempfile.mkdtemp(prefix="archive_"))
    try:
        safe_extract_zip(zip_path, root, extensions=extensions, limits=limits)
        yield root
    finally:
        shutil.rmtree(root, ignore_errors=True)


def natural_key(text: str) -> list[Any]:
    """Sort key that orders numbers numerically: 'Threat Level 2' before 'Threat Level 10'."""
    return [int(part) if part.isdigit() else part.lower() for part in re.split(r"(\d+)", text)]


def find_files(root: Path | str, extensions: set[str]) -> list[Path]:
    """All files under root with one of the given suffixes, in natural path order, skipping clutter."""
    root = Path(root)
    wanted = {ext.lower() for ext in extensions}
    return sorted(
        (
            path for path in root.rglob("*")
            if path.is_file()
            and path.suffix.lower() in wanted
            and not is_ignored_entry(path.relative_to(root).as_posix())
        ),
        key=lambda path: natural_key(path.relative_to(root).as_posix()),
    )


def relative_name(path: Path | str, root: Path | str) -> str:
    """'Main Dir/Threat Level 1/Goblin.docx' style path of a file inside an extracted archive."""
    return Path(path).relative_to(root).as_posix()
