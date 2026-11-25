import type { RuleBook, RuleChapter, RuleSearchResult } from "../models/Rules";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "/api/v1").replace(/\/$/, "");

/** Performs a GET against the rules API and returns the parsed JSON body. */
async function requestJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, { signal });
  if (!response.ok) {
    throw new Error(`Rules API request failed: ${response.status} ${response.statusText}`);
  }
  return (await response.json()) as T;
}

/** Fetches every rulebook with its table of contents (core rules first). */
export function getRuleBooks(signal?: AbortSignal): Promise<RuleBook[]> {
  return requestJson<RuleBook[]>("/rules/books", signal);
}

/** Fetches one rulebook with its table of contents. */
export function getRuleBook(bookId: string, signal?: AbortSignal): Promise<RuleBook> {
  return requestJson<RuleBook>(`/rules/books/${encodeURIComponent(bookId)}`, signal);
}

/** Fetches a whole chapter: its sections plus the entities and images they reference. */
export function getRuleChapter(chapterId: string, signal?: AbortSignal): Promise<RuleChapter> {
  return requestJson<RuleChapter>(`/rules/chapters/${encodeURIComponent(chapterId)}`, signal);
}

/** Searches rules text (supports "quoted phrases" and -excluded words), optionally within one book. */
export function searchRules(query: string, bookId?: string, limit = 20, signal?: AbortSignal): Promise<RuleSearchResult[]> {
  const params = new URLSearchParams({ q: query, limit: String(limit) });
  if (bookId) params.set("bookId", bookId);
  return requestJson<RuleSearchResult[]>(`/rules/search?${params.toString()}`, signal);
}

/** URL of a stored image, for an asset id taken from an ![alt](asset:<id>) link. */
export function assetUrl(assetId: string): string {
  return `${API_BASE_URL}/assets/${encodeURIComponent(assetId)}`;
}

/** What the compendium's Rules tab searches for; null in the UI means no search yet. */
export interface RuleFilterRequest {
  query: string;
  /** Limits the search to one book; null searches every book. */
  bookId: string | null;
}
