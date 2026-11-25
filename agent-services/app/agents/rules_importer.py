"""
rules_importer.py

Import parser for the Journey rules tables (backend/schema/08-rules.sql): `rule_chapters`,
`rule_sections`, `rule_section_entities`, `rule_section_assets`, `assets`, and `book_chapters`.

No AI model is used. Each numbered chapter document (.docx) is walked in reading order:
- Word headings (Title, Heading 1-6) become sections; the heading levels build the section tree
- Paragraph text becomes Markdown, keeping bold/italic, list bullets, line breaks, and tables
- Embedded images become assets, referenced from the Markdown as ![alt](asset:<id>)
- Entity blocks (traits, powers, items, races, jobs, creatures) are not stored as rules text. When the
  entity exists in the database the block becomes an inline card reference ([Name](trait:<id>) on its own
  line); when it does not, the original text is kept and the preview lists it as missing, so the
  dedicated importer can be run first. The rules importer never creates or updates entities.

Archives follow the rules folder layout:
- Chapter files are numbered ("04_Combat(Journey 100).docx"); "00_" files (the whole book) and
  unnumbered files (design docs, templates) are skipped
- Files under "Settings/<Name> Setting/" belong to that setting's content source; other files to Core
- "Creatures/" folders and the Bestiary chapter are skipped (creatures use the creature importer)

Re-importing a chapter updates it in place: sections are matched by slug, changed sections get a
revision ("Imported from <file>"), new ones are added, and removed ones are deleted.
"""

from __future__ import annotations

import hashlib
import os
import re
import struct
import uuid
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, PrivateAttr

from agents.item_importer import key
from agents.trait_importer import normalize_database_url
from helpers.agent_helpers import field_label_of

__all__ = [
    "RuleChapterImport",
    "RulesImportResult",
    "chapter_from_filename",
    "extract_rules_from_archive",
    "extract_rules_from_docx",
    "insert_rule_chapters",
]


EntityType = Literal["trait", "power", "item", "race", "job", "creature"]

# Chapters never imported as rules (their content comes from a dedicated importer).
SKIPPED_CHAPTER_KEYS = {"bestiary"}
CHAPTER_FILE = re.compile(r"^(\d+)_(.+)\.docx$", re.IGNORECASE)
SETTING_FOLDER = re.compile(r"(?i)^(.+?)\s+setting$")


# ----------------------------
# Pydantic models
# ----------------------------

class EntityRef(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: EntityType
    id: str
    name: str


class MissingEntity(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: EntityType
    name: str
    section: str


class RuleAsset(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sha256: str
    content_type: str
    original_filename: str | None = None
    alt_text: str | None = None
    byte_size: int
    width: int | None = None
    height: int | None = None


class RuleSection(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str
    level: int = Field(ge=1, le=6)
    slug: str
    parent_slug: str | None = None
    # Markdown. Images use asset:<sha256> until commit, when they are rewritten to asset:<id>.
    body: str
    entities: list[EntityRef] = Field(default_factory=list)
    asset_shas: list[str] = Field(default_factory=list)


class RuleChapterImport(BaseModel):
    model_config = ConfigDict(extra="forbid")

    file: str
    source_name: str
    title: str
    chapter_key: str
    number: int | None = None
    sections: list[RuleSection] = Field(default_factory=list)
    assets: list[RuleAsset] = Field(default_factory=list)
    missing_entities: list[MissingEntity] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)

    # Image bytes by sha256; kept out of the API response.
    _blobs: dict[str, bytes] = PrivateAttr(default_factory=dict)


class SkippedFile(BaseModel):
    model_config = ConfigDict(extra="forbid")

    file: str
    reason: str


class RulesImportResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    chapters: list[RuleChapterImport]
    skipped_files: list[SkippedFile]
    errors: list[SkippedFile] = Field(default_factory=list)


# ----------------------------
# Reference data
# ----------------------------

@dataclass
class EntityIndex:
    # type -> name key -> (id, display name)
    by_type: dict[str, dict[str, tuple[str, str]]]
    # source name key -> source id
    sources: dict[str, str]


ENTITY_QUERIES: dict[str, str] = {
    "trait": "SELECT id, trait_name FROM traits;",
    "power": "SELECT id, name FROM powers;",
    "item": "SELECT id, name FROM items;",
    "race": "SELECT id, race_name FROM races;",
    "job": "SELECT id, name FROM jobs;",
    "creature": "SELECT id, name FROM creatures;",
}


def fetch_entity_index(database_url: str) -> EntityIndex:
    import psycopg

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        with conn.cursor() as cur:
            by_type: dict[str, dict[str, tuple[str, str]]] = {}
            for entity_type, sql in ENTITY_QUERIES.items():
                cur.execute(sql)
                by_type[entity_type] = {key(name): (str(row_id), name) for row_id, name in cur.fetchall()}
            cur.execute("SELECT id, source_name FROM content_sources;")
            sources = {key(name): str(row_id) for row_id, name in cur.fetchall()}
    return EntityIndex(by_type=by_type, sources=sources)


# ----------------------------
# File names and folders
# ----------------------------

def chapter_from_filename(filename: str) -> tuple[int, str, str] | None:
    """'04_Combat(Journey 100).docx' -> (4, 'Combat', 'combat'); None for unnumbered files."""
    match = CHAPTER_FILE.match(PurePosixPath(filename).name)
    if not match:
        return None
    title = re.sub(r"\([^)]*\)", "", match.group(2)).replace("_", " ")
    title = re.sub(r"\s+", " ", title).strip()
    return int(match.group(1)), title, key(title)


def source_name_for_path(relative_path: str) -> tuple[str | None, str | None]:
    """(source name, skip reason) for a file in the rules archive."""
    folders = PurePosixPath(relative_path).parts[:-1]
    for folder in folders:
        match = SETTING_FOLDER.match(folder)
        if match:
            return match.group(1).strip(), None
    if any(folder.lower() == "settings" for folder in folders):
        return None, "In the Settings folder but not inside a '<Name> Setting' folder."
    return "Core", None


# ----------------------------
# Document walking
# ----------------------------

@dataclass
class Unit:
    """One piece of a section body, in reading order."""
    kind: Literal["line", "table", "image", "card"]
    markdown: str
    # Plain text of a line (for entity detection); empty for tables/images/cards.
    plain: str = ""
    # Lines from the same Word paragraph share a group (joined by a hard line break).
    group: int = 0
    # List paragraphs are joined with single newlines so the list stays together.
    is_list: bool = False


@dataclass
class RawSection:
    title: str
    level: int
    units: list[Unit] = field(default_factory=list)


MARKDOWN_SPECIAL = re.compile(r"([\\`*_\[\]])")
LINE_START_SPECIAL = re.compile(r"^(\s*)([#>+\-]|\d+\.)(\s)")


def escape_markdown(text: str) -> str:
    return MARKDOWN_SPECIAL.sub(r"\\\1", text)


def escape_line_start(line: str) -> str:
    """Stop plain lines such as '- 20% Stealth' or '1. Draw' from turning into lists or headings."""
    return LINE_START_SPECIAL.sub(lambda m: f"{m.group(1)}\\{m.group(2)}{m.group(3)}", line)


def wrap(text: str, bold: bool, italic: bool) -> str:
    """Wrap text in emphasis markers, keeping surrounding spaces outside them."""
    if not (bold or italic) or not text.strip():
        return text
    marker = "***" if bold and italic else "**" if bold else "*"
    leading = text[: len(text) - len(text.lstrip())]
    trailing = text[len(text.rstrip()):]
    return f"{leading}{marker}{text.strip()}{marker}{trailing}"


def heading_level(style_name: str) -> int | None:
    if style_name == "Title":
        return 1
    match = re.fullmatch(r"Heading (\d)", style_name)
    return min(int(match.group(1)), 6) if match else None


def list_level(paragraph: Any) -> int | None:
    ppr = paragraph._p.pPr
    if ppr is not None and ppr.numPr is not None:
        ilvl = ppr.numPr.ilvl
        return int(ilvl.val) if ilvl is not None and ilvl.val is not None else 0
    style = paragraph.style.name if paragraph.style is not None else ""
    if style.lower().startswith("list"):
        match = re.search(r"(\d+)\s*$", style)
        return int(match.group(1)) - 1 if match else 0
    return None


def image_size(blob: bytes) -> tuple[int | None, int | None]:
    """Pixel size from PNG, GIF, or JPEG headers (no image library needed)."""
    try:
        if blob[:8] == b"\x89PNG\r\n\x1a\n":
            width, height = struct.unpack(">II", blob[16:24])
            return width, height
        if blob[:6] in (b"GIF87a", b"GIF89a"):
            width, height = struct.unpack("<HH", blob[6:10])
            return width, height
        if blob[:2] == b"\xff\xd8":
            i = 2
            while i + 9 < len(blob):
                if blob[i] != 0xFF:
                    i += 1
                    continue
                marker = blob[i + 1]
                length = struct.unpack(">H", blob[i + 2:i + 4])[0]
                if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
                    height, width = struct.unpack(">HH", blob[i + 5:i + 9])
                    return width, height
                i += 2 + length
    except struct.error:
        pass
    return None, None


@dataclass
class WalkedDocument:
    sections: list[RawSection]
    assets: dict[str, tuple[RuleAsset, bytes]]


def walk_docx(path: Path | str, chapter_title: str) -> WalkedDocument:
    """Read a .docx into sections (by heading) of Markdown units, collecting embedded images."""
    from docx import Document
    from docx.oxml.ns import qn
    from docx.table import Table
    from docx.text.paragraph import Paragraph
    from docx.text.run import Run

    doc = Document(str(path))
    sections: list[RawSection] = []
    current = RawSection(title=chapter_title, level=1)
    assets: dict[str, tuple[RuleAsset, bytes]] = {}
    group = 0

    def add_images(run_element: Any, units: list[Unit]) -> None:
        for blip in run_element.iter(qn("a:blip")):
            rel_id = blip.get(qn("r:embed"))
            part = doc.part.related_parts.get(rel_id) if rel_id else None
            if part is None or not hasattr(part, "blob"):
                continue
            blob = part.blob
            sha = hashlib.sha256(blob).hexdigest()
            doc_pr = next(iter(run_element.iter(qn("wp:docPr"))), None)
            alt = None
            if doc_pr is not None:
                alt = (doc_pr.get("descr") or doc_pr.get("title") or "").strip() or None
            if sha not in assets:
                width, height = image_size(blob)
                assets[sha] = (
                    RuleAsset(
                        sha256=sha,
                        content_type=getattr(part, "content_type", "application/octet-stream"),
                        original_filename=PurePosixPath(str(part.partname)).name,
                        alt_text=alt,
                        byte_size=len(blob),
                        width=width,
                        height=height,
                    ),
                    blob,
                )
            label = escape_markdown(alt or assets[sha][0].original_filename or "image")
            units.append(Unit(kind="image", markdown=f"![{label}](asset:{sha})"))

    def paragraph_units(paragraph: Any) -> list[Unit]:
        nonlocal group
        group += 1
        level = list_level(paragraph)
        segments: list[tuple[str, bool, bool]] = []
        images: list[Unit] = []

        # Iterate the XML so runs inside hyperlinks are included.
        for run_element in paragraph._p.iter(qn("w:r")):
            run = Run(run_element, paragraph)
            if run.text:
                segments.append((run.text, bool(run.bold), bool(run.italic)))
            add_images(run_element, images)

        # Split into lines at Word line breaks, keeping each segment's formatting.
        lines: list[list[tuple[str, bool, bool]]] = [[]]
        for text, bold, italic in segments:
            parts = text.replace("\t", " ").split("\n")
            for n, part in enumerate(parts):
                if n:
                    lines.append([])
                if part:
                    lines[-1].append((part, bold, italic))

        units: list[Unit] = []
        for n, line in enumerate(lines):
            plain = "".join(text for text, _, _ in line).strip()
            if not plain:
                continue
            merged: list[tuple[str, bool, bool]] = []
            for text, bold, italic in line:
                if merged and merged[-1][1:] == (bold, italic):
                    merged[-1] = (merged[-1][0] + text, bold, italic)
                else:
                    merged.append((text, bold, italic))
            markdown = "".join(wrap(escape_markdown(text), bold, italic) for text, bold, italic in merged).strip()
            if level is not None:
                indent = "  " * level
                markdown = f"{indent}- {markdown}" if n == 0 else f"{indent}  {markdown}"
            else:
                markdown = escape_line_start(markdown)
            units.append(Unit(kind="line", markdown=markdown, plain=plain, group=group, is_list=level is not None))
        return units + images

    def table_unit(table: Any) -> Unit | None:
        rows = []
        for row in table.rows:
            cells = [re.sub(r"\s+", " ", cell.text).strip().replace("|", "\\|") for cell in row.cells]
            rows.append(cells)
        rows = [row for row in rows if any(row)]
        if not rows:
            return None
        width = max(len(row) for row in rows)
        rows = [row + [""] * (width - len(row)) for row in rows]
        lines = ["| " + " | ".join(rows[0]) + " |", "|" + " --- |" * width]
        lines += ["| " + " | ".join(row) + " |" for row in rows[1:]]
        return Unit(kind="table", markdown="\n".join(lines))

    for child in doc.element.body.iterchildren():
        if child.tag == qn("w:p"):
            paragraph = Paragraph(child, doc)
            style = paragraph.style.name if paragraph.style is not None else ""
            level = heading_level(style)
            text = paragraph.text.strip()
            if level is not None and text:
                sections.append(current)
                current = RawSection(title=text.rstrip(":").strip() or text, level=level)
                continue
            current.units.extend(paragraph_units(paragraph))
        elif child.tag == qn("w:tbl"):
            unit = table_unit(Table(child, doc))
            if unit:
                current.units.append(unit)

    sections.append(current)
    # The leading section before the first heading is kept only if it has content.
    if not sections[0].units:
        sections = sections[1:]
    return WalkedDocument(sections=sections, assets=assets)


# ----------------------------
# Entity detection
# ----------------------------

def is_name_like(text: str) -> bool:
    cleaned = text.strip()
    return (
        bool(cleaned)
        and ":" not in cleaned
        and not cleaned.endswith((".", "!", "?"))
        and len(cleaned.split()) <= 8
        and bool(re.search(r"[A-Za-z]", cleaned))
    )


def classify_block(labels: set[str], has_traits_child: bool) -> EntityType | None:
    """Which entity a block of labelled fields describes, judged by its labels."""
    if "threat lvl" in labels:
        return "creature"
    if "size" in labels and ({"movement", "speed", "languages", "language"} & labels):
        return "race"
    if "job description" in labels or ("description" in labels and has_traits_child and "lvl" not in labels):
        return "job"
    if "lvl" in labels and "type" in labels and ({"components", "duration", "range", "cost"} & labels):
        return "power"
    if "lvl" in labels and "effect" in labels:
        return "trait"
    item_labels = {"rarity", "weapon type", "strength req", "stackable", "charges", "attunement",
                   "magic item effect", "armor effect", "weapon effect"}
    if "lvl" not in labels and (item_labels & labels or ("cost" in labels and {"effect", "description"} & labels)):
        return "item"
    return None


# Template names in the rules ("Trait Name", "Power Name", "Name") are not entities.
PLACEHOLDER_NAME = re.compile(r"(?i)^((trait|power|item|creature|job|race|book)\s*)?name$")

TRAITS_SUBSECTION_TITLES = {"traits", "job traits", "racial traits", "race traits"}
RARITIES = {"common", "uncommon", "rare", "very rare"}


def label_value(units: list[Unit], label: str) -> str:
    """Text after the first '<label>:' among the units."""
    for unit in units:
        if field_label_of(unit.plain) == label:
            return unit.plain.split(":", 1)[1].strip()
    return ""


def has_real_values(entity_type: str, units: list[Unit]) -> bool:
    """
    Templates and examples in the rules ("Trait Name / LVL: / Effect:", "Threat LVL: Threat level of
    Creature") look like entity blocks; real entities have a real value in their key field.
    """
    if entity_type in ("trait", "power"):
        return bool(re.fullmatch(r"\d+", label_value(units, "lvl")))
    if entity_type == "creature":
        return bool(re.match(r"\d+", label_value(units, "threat lvl")))
    if entity_type == "item":
        rarity = label_value(units, "rarity").lower()
        return rarity in RARITIES or bool(re.search(r"\d", label_value(units, "cost")))
    return True


def entity_name(entity_type: str, name: str) -> str:
    """Name used for lookup ('Standard Arrows x10' -> 'Standard Arrows')."""
    if entity_type == "item":
        name = re.sub(r"\s+x\d+$", "", name.strip(), flags=re.IGNORECASE)
    return name.strip()


@dataclass
class SectionResult:
    units: list[Unit]
    entities: list[EntityRef]
    missing: list[MissingEntity]
    # True when the section itself is a job/race card; its subsections are then dropped.
    consumes_children: bool = False


def replace_entity_blocks(section: RawSection, has_traits_child: bool, index: EntityIndex) -> SectionResult:
    """
    Find entity blocks in a section and replace the ones that exist in the database with card units.

    Blocks start at a name-like line followed by a field label line. The section title counts as the
    first line, so "### Battle Tactics" followed by "LVL: 1 ..." is itself a block.
    """
    title_unit = Unit(kind="line", markdown="", plain=section.title, group=-1)
    units = [title_unit] + section.units

    def is_label(i: int) -> bool:
        return units[i].kind == "line" and field_label_of(units[i].plain) is not None

    starts = [
        i for i in range(len(units) - 1)
        if units[i].kind == "line" and is_name_like(units[i].plain) and is_label(i + 1)
    ]

    result_units: list[Unit] = []
    entities: list[EntityRef] = []
    missing: list[MissingEntity] = []
    consumes_children = False
    position = 1  # skip the title unit when copying unmatched content

    for n, start in enumerate(starts):
        end = starts[n + 1] if n + 1 < len(starts) else len(units)
        # A block stops at a table/image; those stay as rules content.
        for j in range(start + 1, end):
            if units[j].kind != "line":
                end = j
                break
        labels = {label for u in units[start + 1:end] if (label := field_label_of(u.plain))}
        entity_type = classify_block(labels, has_traits_child and start == 0)
        if (
            entity_type is None
            or PLACEHOLDER_NAME.match(units[start].plain.strip())
            or not has_real_values(entity_type, units[start + 1:end])
        ):
            continue

        name = entity_name(entity_type, units[start].plain)
        found = index.by_type[entity_type].get(key(name))

        result_units.extend(units[max(position, 1):start])
        if found:
            entity_id, display = found
            entities.append(EntityRef(type=entity_type, id=entity_id, name=display))
            result_units.append(Unit(kind="card", markdown=f"[{escape_markdown(display)}]({entity_type}:{entity_id})"))
            if start == 0 and entity_type in ("job", "race"):
                consumes_children = True
        else:
            missing.append(MissingEntity(type=entity_type, name=name, section=section.title))
            result_units.extend(units[max(start, 1):end])
        position = end

    result_units.extend(units[max(position, 1):])
    return SectionResult(units=result_units, entities=entities, missing=missing, consumes_children=consumes_children)


def render_units(units: list[Unit]) -> str:
    """Join units into Markdown: lines of one paragraph with hard breaks, list items with newlines."""
    parts: list[str] = []
    previous: Unit | None = None
    for unit in units:
        if previous is not None:
            if unit.kind == "line" and previous.kind == "line" and unit.group == previous.group:
                parts.append("\\\n")
            elif unit.is_list and previous.is_list:
                parts.append("\n")
            else:
                parts.append("\n\n")
        parts.append(unit.markdown)
        previous = unit
    return "".join(parts).strip()


def slugify(title: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", key(title)).strip("-")
    return slug or "section"


# ----------------------------
# Chapter extraction
# ----------------------------

def extract_rules_from_docx(
    path: Path | str,
    relative_name: str,
    source_name: str,
    number: int | None,
    title: str,
    chapter_key: str,
    index: EntityIndex,
) -> RuleChapterImport:
    walked = walk_docx(path, title)
    raw = walked.sections
    chapter = RuleChapterImport(
        file=relative_name, source_name=source_name, title=title, chapter_key=chapter_key, number=number,
    )

    used_slugs: dict[str, int] = {}
    parents: list[tuple[int, str]] = []  # stack of (level, slug)
    # After a job/race card, its own "Traits:" subsection (and anything nested in it) is dropped, since
    # the card shows the traits. Only that subsection: a mis-styled heading must not swallow siblings.
    card_level: int | None = None
    skip_deeper_than: int | None = None
    used_assets: set[str] = set()

    for i, section in enumerate(raw):
        if skip_deeper_than is not None:
            if section.level > skip_deeper_than:
                continue
            skip_deeper_than = None
        if card_level is not None:
            if section.level > card_level and key(section.title) in TRAITS_SUBSECTION_TITLES:
                skip_deeper_than = section.level
                card_level = None
                continue
            card_level = None

        has_traits_child = (
            i + 1 < len(raw) and raw[i + 1].level > section.level
            and key(raw[i + 1].title) in TRAITS_SUBSECTION_TITLES
        )
        result = replace_entity_blocks(section, has_traits_child, index)
        chapter.missing_entities.extend(result.missing)
        if result.consumes_children:
            card_level = section.level

        base = slugify(section.title)
        used_slugs[base] = used_slugs.get(base, 0) + 1
        slug = base if used_slugs[base] == 1 else f"{base}-{used_slugs[base]}"

        while parents and parents[-1][0] >= section.level:
            parents.pop()
        parent_slug = parents[-1][1] if parents else None
        parents.append((section.level, slug))

        shas = [m for m in re.findall(r"\(asset:([0-9a-f]{64})\)", render_units(result.units))]
        used_assets.update(shas)
        chapter.sections.append(
            RuleSection(
                title=section.title,
                level=section.level,
                slug=slug,
                parent_slug=parent_slug,
                body=render_units(result.units),
                entities=result.entities,
                asset_shas=list(dict.fromkeys(shas)),
            )
        )

    chapter.assets = [walked.assets[sha][0] for sha in walked.assets if sha in used_assets]
    chapter._blobs = {sha: walked.assets[sha][1] for sha in used_assets}
    if chapter.missing_entities:
        chapter.warnings.append(
            f"{len(chapter.missing_entities)} entity block(s) are not in the database yet; their text was kept. "
            "Import them with the matching importer, then re-import this chapter to turn them into cards."
        )
    return chapter


def extract_rules_from_archive(zip_path: str, database_url: str) -> RulesImportResult:
    """Extract every numbered chapter in a rules archive (see the module docstring for the layout)."""
    from helpers.archive_helpers import extracted_zip, find_files, relative_name

    index = fetch_entity_index(database_url)
    chapters: list[RuleChapterImport] = []
    skipped: list[SkippedFile] = []
    errors: list[SkippedFile] = []

    with extracted_zip(zip_path, extensions={".docx"}) as root:
        for path in find_files(root, {".docx"}):
            name = relative_name(path, root)

            if any(part.lower() == "creatures" for part in PurePosixPath(name).parts[:-1]):
                continue  # creature stat blocks: use the creature archive importer
            parsed = chapter_from_filename(name)
            if parsed is None:
                skipped.append(SkippedFile(file=name, reason="Not a numbered chapter file."))
                continue
            number, title, chapter_key = parsed
            if number == 0:
                skipped.append(SkippedFile(file=name, reason="00_ files are the whole book, not a chapter."))
                continue
            if chapter_key in SKIPPED_CHAPTER_KEYS:
                skipped.append(SkippedFile(file=name, reason="Imported by its own importer (creatures)."))
                continue

            source_name, reason = source_name_for_path(name)
            if source_name is None:
                skipped.append(SkippedFile(file=name, reason=reason or "Unknown source."))
                continue
            if key(source_name) not in index.sources:
                errors.append(SkippedFile(file=name, reason=f"No content source named {source_name!r}."))
                continue

            try:
                chapters.append(extract_rules_from_docx(path, name, source_name, number, title, chapter_key, index))
            except Exception as exc:  # corrupt document: report and continue with the rest
                errors.append(SkippedFile(file=name, reason=f"Could not read the document: {exc}"))

    return RulesImportResult(chapters=chapters, skipped_files=skipped, errors=errors)


# ----------------------------
# Database insert
# ----------------------------

def asset_extension(content_type: str, filename: str | None) -> str:
    suffix = PurePosixPath(filename or "").suffix.lower()
    if suffix:
        return suffix
    return {"image/png": ".png", "image/jpeg": ".jpg", "image/gif": ".gif", "image/webp": ".webp"}.get(content_type, ".bin")


def insert_rule_chapters(
    chapters: list[RuleChapterImport],
    database_url: str,
    asset_dir: str | None,
) -> dict[str, Any]:
    """
    Upsert chapters, their sections (matched by slug), entity/asset links, assets, and book order in one
    transaction. Image files are written to asset_dir/<source>/<sha[:2]>/<sha>.<ext>.
    """
    import psycopg

    needs_assets = any(chapter.assets for chapter in chapters)
    if needs_assets and not asset_dir:
        raise RuntimeError("ASSET_STORAGE_DIR is not set, so images cannot be stored.")

    stats = {"chapters": 0, "sections_inserted": 0, "sections_updated": 0, "sections_deleted": 0,
             "assets_stored": 0, "chapter_ids": []}

    with psycopg.connect(normalize_database_url(database_url)) as conn:
        with conn.cursor() as cur:
            cur.execute("SET CONSTRAINTS ALL DEFERRED;")
            cur.execute("SELECT id, source_name FROM content_sources;")
            source_ids = {key(name): row_id for row_id, name in cur.fetchall()}

            for chapter in chapters:
                source_id = source_ids[key(chapter.source_name)]

                cur.execute(
                    """
                    INSERT INTO rule_chapters (source_id, title, chapter_key, number)
                    VALUES (%s, %s, %s, %s)
                    ON CONFLICT (source_id, chapter_key)
                    DO UPDATE SET title = EXCLUDED.title, number = EXCLUDED.number, updated_at = NOW()
                    RETURNING id;
                    """,
                    (source_id, chapter.title, chapter.chapter_key, chapter.number),
                )
                chapter_id = cur.fetchone()[0]
                stats["chapter_ids"].append(str(chapter_id))

                # Assets: reuse by content hash within the source, otherwise store the file and insert a row.
                asset_ids: dict[str, str] = {}
                blobs = chapter._blobs
                for asset in chapter.assets:
                    cur.execute("SELECT id FROM assets WHERE source_id = %s AND sha256 = %s;", (source_id, asset.sha256))
                    row = cur.fetchone()
                    if row:
                        asset_ids[asset.sha256] = str(row[0])
                        continue
                    relative = f"{key(chapter.source_name).replace(' ', '-')}/{asset.sha256[:2]}/{asset.sha256}{asset_extension(asset.content_type, asset.original_filename)}"
                    target = Path(asset_dir) / relative  # type: ignore[arg-type]
                    target.parent.mkdir(parents=True, exist_ok=True)
                    if not target.exists():
                        target.write_bytes(blobs[asset.sha256])
                    asset_id = str(uuid.uuid4())
                    cur.execute(
                        """
                        INSERT INTO assets (id, source_id, file_path, original_filename, content_type, byte_size,
                                            width, height, alt_text, sha256)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s);
                        """,
                        (asset_id, source_id, relative, asset.original_filename, asset.content_type, asset.byte_size,
                         asset.width, asset.height, asset.alt_text, asset.sha256),
                    )
                    asset_ids[asset.sha256] = asset_id
                    stats["assets_stored"] += 1

                # Sections: match by slug so ids (and revision history) survive re-imports.
                cur.execute("SELECT slug, id, title, body FROM rule_sections WHERE chapter_id = %s;", (chapter_id,))
                existing = {slug: (row_id, title, body) for slug, row_id, title, body in cur.fetchall()}
                new_slugs = {section.slug for section in chapter.sections}

                # Detach parents first so deleting removed sections cannot cascade into kept ones.
                cur.execute("UPDATE rule_sections SET parent_id = NULL WHERE chapter_id = %s;", (chapter_id,))
                removed = [row[0] for slug, row in existing.items() if slug not in new_slugs]
                if removed:
                    cur.execute("DELETE FROM rule_sections WHERE id = ANY(%s);", (removed,))
                    stats["sections_deleted"] += len(removed)

                section_ids: dict[str, str] = {}
                note = f"Imported from {chapter.file}"
                for order, section in enumerate(chapter.sections):
                    body = section.body
                    for sha, asset_id in asset_ids.items():
                        body = body.replace(f"(asset:{sha})", f"(asset:{asset_id})")
                    parent_id = section_ids.get(section.parent_slug) if section.parent_slug else None

                    if section.slug in existing:
                        section_id, old_title, old_body = existing[section.slug]
                        if old_title != section.title or old_body != body:
                            cur.execute(
                                "INSERT INTO rule_section_revisions (section_id, title, body, note) VALUES (%s, %s, %s, %s);",
                                (section_id, old_title, old_body, note),
                            )
                            stats["sections_updated"] += 1
                        cur.execute(
                            """
                            UPDATE rule_sections
                            SET parent_id = %s, title = %s, level = %s, body = %s, sort_order = %s, updated_at = NOW()
                            WHERE id = %s;
                            """,
                            (parent_id, section.title, section.level, body, order, section_id),
                        )
                    else:
                        section_id = str(uuid.uuid4())
                        cur.execute(
                            """
                            INSERT INTO rule_sections (id, chapter_id, parent_id, title, level, slug, body, sort_order)
                            VALUES (%s, %s, %s, %s, %s, %s, %s, %s);
                            """,
                            (section_id, chapter_id, parent_id, section.title, section.level, section.slug, body, order),
                        )
                        stats["sections_inserted"] += 1
                    section_ids[section.slug] = str(section_id)

                    cur.execute("DELETE FROM rule_section_entities WHERE section_id = %s;", (section_id,))
                    for n, entity in enumerate(section.entities):
                        cur.execute(
                            f"INSERT INTO rule_section_entities (section_id, {entity.type}_id, sort_order) VALUES (%s, %s, %s);",
                            (section_id, entity.id, n),
                        )
                    cur.execute("DELETE FROM rule_section_assets WHERE section_id = %s;", (section_id,))
                    for n, sha in enumerate(section.asset_shas):
                        cur.execute(
                            "INSERT INTO rule_section_assets (section_id, asset_id, sort_order) VALUES (%s, %s, %s) "
                            "ON CONFLICT DO NOTHING;",
                            (section_id, asset_ids[sha], n),
                        )

                # Book order: add the chapter to its source's book (by chapter number).
                cur.execute("SELECT id FROM books WHERE source_id = %s ORDER BY created_at LIMIT 1;", (source_id,))
                book = cur.fetchone()
                if book and chapter.number is not None:
                    cur.execute(
                        """
                        INSERT INTO book_chapters (book_id, chapter_id, sort_order) VALUES (%s, %s, %s)
                        ON CONFLICT (book_id, chapter_id) DO UPDATE SET sort_order = EXCLUDED.sort_order, updated_at = NOW();
                        """,
                        (book[0], chapter_id, chapter.number),
                    )
                stats["chapters"] += 1

        conn.commit()

    return stats
