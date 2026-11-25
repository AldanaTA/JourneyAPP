import type { Creature } from "./Creature";
import type { Item } from "./Item";
import type { Job } from "./Job";
import type { Power } from "./Power";
import type { Race } from "./Race";
import type { Trait } from "./Trait";

/** A chapter as listed in a book's table of contents. */
export interface RuleChapterSummary {
  id: string;
  title: string;
  number: number | null;
  sortOrder: number;
  contentSourceName: string;
}

/** A rulebook with its chapters in reading order. */
export interface RuleBook {
  id: string;
  title: string;
  description: string | null;
  contentSourceName: string;
  chapters: RuleChapterSummary[];
}

/** One heading of a chapter and the Markdown under it; parentId builds the tree. */
export interface RuleSection {
  id: string;
  parentId: string | null;
  title: string;
  /** Heading level, 1-6. */
  level: number;
  /** Anchor id for deep links, unique within the chapter. */
  slug: string;
  /** Markdown; entity links look like [Name](trait:<id>) and images like ![alt](asset:<id>). */
  body: string;
  sortOrder: number;
}

/** Metadata for an image used in a chapter. */
export interface RuleAsset {
  id: string;
  contentType: string;
  width: number | null;
  height: number | null;
  altText: string | null;
}

/** The kinds of entity a rules body can link to (the scheme of the link). */
export type RuleEntityType = "trait" | "power" | "item" | "race" | "job" | "creature";

/** A whole chapter with every entity and image its sections reference, keyed by id. */
export interface RuleChapter {
  id: string;
  title: string;
  number: number | null;
  description: string | null;
  contentSourceName: string;
  sections: RuleSection[];
  traits: Record<string, Trait>;
  powers: Record<string, Power>;
  items: Record<string, Item>;
  races: Record<string, Race>;
  jobs: Record<string, Job>;
  creatures: Record<string, Creature>;
  assets: Record<string, RuleAsset>;
}

/** A section matching a rules search. In snippet, matched words are wrapped in « and ». */
export interface RuleSearchResult {
  sectionId: string;
  sectionTitle: string;
  slug: string;
  chapterId: string;
  chapterTitle: string;
  bookId: string | null;
  snippet: string;
  rank: number;
}
