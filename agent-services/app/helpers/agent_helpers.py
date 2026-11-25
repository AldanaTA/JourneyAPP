"""
Reusable helper utilities for Journey import agents.

This module intentionally contains generic functionality that can be reused by
other agents, such as file text extraction and character-count batching.
"""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import re
from pathlib import Path
from typing import Any, Callable, Sequence, TypeVar, cast

import math
import os
from pathlib import Path

T = TypeVar("T")
R = TypeVar("R")
    
def strip_nul(text: str) -> str:
    """Remove NUL (0x00) bytes, which PostgreSQL text columns reject."""
    return text.replace("\x00", "")


def normalize_text(text: str) -> str:
    """Normalize line endings and collapse excessive blank lines."""
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def read_txt(path: Path) -> str:
    """Read plain text from a .txt file (utf-8-sig drops the byte-order mark Windows editors add)."""
    return path.read_text(encoding="utf-8-sig", errors="replace")


# Field labels and section headings used by the importers. Word list formatting on these lines is
# ignored so the importers still recognise them (see read_docx).
KNOWN_FIELD_LABELS = [
    # Shared / traits / powers
    "Name", "Type", "Description", "Effect", "Cost", "Uses", "Uses or Resource Cost", "Resource Cost",
    "LVL", "LVL UP", "LVL UP Cost", "Purchase Cost", "Empower", "Components", "Material Components",
    "Range", "Area", "Duration",
    # Jobs / races
    "Job", "Job Name", "Job Description", "Race", "Race Name", "Race Description", "Traits", "Job Traits",
    "Racial Traits", "Race Traits", "Size", "Movement", "Speed", "Languages", "Language",
    # Items
    "Rarity", "Stackable", "Charges", "Attunement", "Strength Req", "Weapon Type", "Weapon Properties",
    "Damage", "Mag", "Skills", "Skill Bonus", "Sessions", "Magic Item Effect", "Armor Effect",
    "Weapon Effect", "Defense",
    # Creatures
    "Threat LVL", "Lore", "Combat", "Augments", "Abilities", "Immune", "Resistant", "Weak", "Passives",
    "Triggers", "Declared", "HP", "WP", "MP", "EP", "PRIO", "MVM", "MV", "PD", "MD",
]

_FIELD_LABEL_LINE = re.compile(
    r"(?i)^\s*("
    + "|".join(re.escape(label) for label in sorted(KNOWN_FIELD_LABELS, key=len, reverse=True))
    + r")\s*(?::|$)"
)


def is_field_label_line(text: str) -> bool:
    """True for importer field labels ("Effect: ...") and section headings ("Lore", "Passives:")."""
    return bool(_FIELD_LABEL_LINE.match(text))


def field_label_of(text: str) -> str | None:
    """The known field label a line starts with, lowercased ("LVL UP: x" -> "lvl up"), or None."""
    match = _FIELD_LABEL_LINE.match(text)
    return re.sub(r"\s+", " ", match.group(1).lower()) if match else None


def _looks_like_block_name(text: str, next_text: str | None) -> bool:
    """A short title line (no colon, not a sentence) directly followed by a field label."""
    return (
        next_text is not None
        and ":" not in text
        and not text.rstrip().endswith((".", "!", "?"))
        and len(text.split()) <= 8
        and is_field_label_line(next_text)
    )


def _list_level(paragraph: Any) -> int | None:
    """Indent level of a Word list (bullet or numbered) paragraph, or None if it is not in a list."""
    ppr = paragraph._p.pPr
    if ppr is not None and ppr.numPr is not None:
        ilvl = ppr.numPr.ilvl
        return int(ilvl.val) if ilvl is not None and ilvl.val is not None else 0
    style = paragraph.style.name if paragraph.style is not None else ""
    if style.lower().startswith("list"):
        # Built-in styles carry the level in their name: "List Bullet 2" is the second level.
        match = re.search(r"(\d+)\s*$", style)
        return int(match.group(1)) - 1 if match else 0
    return None


def read_docx(path: Path) -> str:
    """
    Extract text from a .docx file, including basic table contents.

    Word list items (bullets and numbering) are prefixed with "• " (indented by list level) so option
    lists stay readable after import. Lines the importers rely on are left unprefixed: field labels,
    section headings, and block names (a short title line followed by a field label).
    """
    try:
        from docx import Document
    except ImportError as exc:
        raise RuntimeError("Missing dependency. Install with: pip install python-docx") from exc

    doc = Document(path)
    paragraphs = [(paragraph, paragraph.text.strip()) for paragraph in doc.paragraphs]
    paragraphs = [(paragraph, text) for paragraph, text in paragraphs if text]
    chunks: list[str] = []

    for index, (paragraph, text) in enumerate(paragraphs):
        level = _list_level(paragraph)
        next_text = paragraphs[index + 1][1] if index + 1 < len(paragraphs) else None
        if level is not None and not is_field_label_line(text) and not _looks_like_block_name(text, next_text):
            text = "  " * level + "• " + text
        chunks.append(text)

    # Also read tables, because imported content may be formatted as tables.
    for table in doc.tables:
        for row in table.rows:
            cells = [cell.text.strip() for cell in row.cells]
            cells = [cell for cell in cells if cell]
            if cells:
                chunks.append(" | ".join(cells))

    return "\n".join(chunks)


def read_pdf(path: Path) -> str:
    """Extract text from a PDF file using pypdf."""
    try:
        from pypdf import PdfReader
    except ImportError as exc:
        raise RuntimeError("Missing dependency. Install with: pip install pypdf") from exc

    reader = PdfReader(str(path))
    pages: list[str] = []

    for i, page in enumerate(reader.pages, start=1):
        page_text = page.extract_text() or ""
        pages.append(f"\n--- PAGE {i} ---\n{page_text}")

    return "\n".join(pages)


def extract_text(path: Path | str) -> str:
    """Extract text from supported file types: .txt, .docx, and .pdf."""
    path = Path(path)
    suffix = path.suffix.lower()

    if suffix == ".txt":
        return strip_nul(read_txt(path))

    if suffix == ".docx":
        return strip_nul(read_docx(path))

    if suffix == ".pdf":
        return strip_nul(read_pdf(path))

    raise ValueError(f"Unsupported file type: {suffix}. Use .txt, .docx, or .pdf.")


def batch_by_char_count(
    items: Sequence[T],
    max_chars: int,
    text_getter: Callable[[T], str],
) -> list[list[T]]:
    """
    Split items into batches using the character length of each item's text.

    Oversized individual items are placed into their own batch rather than
    being dropped or split.
    """
    if max_chars <= 0:
        raise ValueError("max_chars must be greater than 0.")

    batches: list[list[T]] = []
    current: list[T] = []
    current_size = 0

    for item in items:
        item_size = len(text_getter(item))

        if current and current_size + item_size > max_chars:
            batches.append(current)
            current = []
            current_size = 0

        current.append(item)
        current_size += item_size

    if current:
        batches.append(current)

    return batches


def run_threaded_batches(
    batches: Sequence[Sequence[T]],
    worker: Callable[[list[T]], R],
    max_workers: int = 4,
) -> list[R]:
    """
    Run a batch worker concurrently while preserving the original batch order.

    This is useful for import agents that send independent batches to an API.
    Each batch is converted to a list before being passed to the worker so the
    worker can safely iterate over it multiple times.
    """
    if max_workers <= 0:
        raise ValueError("max_workers must be greater than 0.")

    if not batches:
        return []

    worker_count = min(max_workers, len(batches))
    ordered_results: list[Any] = [None] * len(batches)

    with ThreadPoolExecutor(max_workers=worker_count) as executor:
        future_to_index = {
            executor.submit(worker, list(batch)): index
            for index, batch in enumerate(batches)
        }

        for future in as_completed(future_to_index):
            index = future_to_index[future]
            ordered_results[index] = future.result()

    return cast(list[R], ordered_results)


def write_json_file(data: Any, output_path: Path | str) -> None:
    """Write data to a UTF-8 JSON file with readable indentation."""
    output_path = Path(output_path)
    output_path.write_text(
        json.dumps(data, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )

# Worker count detection logic:
def _get_cgroup_cpu_count() -> int | None:
    """
    Detect CPU limit inside Docker/Linux cgroups.
    Returns None if no cgroup CPU limit is detected.
    """

    # cgroup v2
    cpu_max_path = Path("/sys/fs/cgroup/cpu.max")
    if cpu_max_path.exists():
        try:
            quota, period = cpu_max_path.read_text().strip().split()

            if quota != "max":
                quota_int = int(quota)
                period_int = int(period)

                if quota_int > 0 and period_int > 0:
                    return max(1, math.floor(quota_int / period_int))
        except Exception:
            pass

    # cgroup v1
    quota_path = Path("/sys/fs/cgroup/cpu/cpu.cfs_quota_us")
    period_path = Path("/sys/fs/cgroup/cpu/cpu.cfs_period_us")

    if quota_path.exists() and period_path.exists():
        try:
            quota = int(quota_path.read_text().strip())
            period = int(period_path.read_text().strip())

            if quota > 0 and period > 0:
                return max(1, math.floor(quota / period))
        except Exception:
            pass

    return None


def get_max_workers(
    *,
    task_type: str = "io",
    min_workers: int = 2,
    max_workers: int = 4,
) -> int:
    """
    Automatically choose a safe worker count based on available CPU.

    task_type:
        "cpu" -> good for CPU-heavy work
        "io"  -> good for file reading, API calls, uploads, DB inserts, etc.
    """

    # Manual override from .env/docker compose if needed
    env_value = os.getenv("MAX_WORKERS")
    if env_value:
        try:
            return max(1, int(env_value))
        except ValueError:
            pass

    cpu_count = (
        _get_cgroup_cpu_count()
        or os.cpu_count()
        or 1
    )

    if task_type == "cpu":
        # Leave at least one CPU free when possible
        workers = max(1, cpu_count - 1)
    else:
        # IO-bound work can use more threads than CPUs
        workers = cpu_count * 4

    return max(min_workers, min(workers, max_workers))

def extract_labeled_sections(block_text: str, labels: Sequence[str]) -> dict[str, list[str]]:
    """
    Read labelled fields from a content block, keeping each field's text exactly as written,
    line breaks included.

    A field starts at a line beginning with one of `labels` followed by ":" (case-insensitive) and runs
    until the next such line. Lines that look like "Name: text" but whose name is not in `labels`
    (e.g. trait options such as "Disarm: ...") stay inside the current field. The block's first line
    (its name) is skipped. Blank lines inside a field are kept as a single paragraph break.

    Returns {label lowercased: [text of each occurrence]}; a label can occur more than once.
    """
    # Longest labels first so "LVL UP Cost" wins over "LVL UP", which wins over "LVL".
    ordered = sorted({label.strip().rstrip(":") for label in labels}, key=len, reverse=True)
    pattern = re.compile(
        r"(?i)^\s*(" + "|".join(re.escape(label) for label in ordered) + r")\s*:\s*(.*)$"
    )

    sections: dict[str, list[str]] = {}
    current: list[str] | None = None

    for line in block_text.split("\n")[1:]:
        match = pattern.match(line)
        if match:
            name = re.sub(r"\s+", " ", match.group(1).lower())
            current = [match.group(2).rstrip()] if match.group(2).strip() else []
            sections.setdefault(name, []).append(current)  # type: ignore[arg-type]
            continue
        if current is not None:
            current.append(line.rstrip())

    def finish(lines: list[str]) -> str:
        text = "\n".join(line.strip() for line in lines).strip()
        return re.sub(r"\n{3,}", "\n\n", text)

    return {name: [finish(lines) for lines in occurrences] for name, occurrences in sections.items()}  # type: ignore[arg-type]
