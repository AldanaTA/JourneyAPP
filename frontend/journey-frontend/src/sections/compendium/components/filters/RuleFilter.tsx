import { SlidersHorizontal, Search, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import type { FormEvent } from "react";
import { getRuleBooks } from "@/apis/rulesApi";
import type { RuleFilterRequest } from "@/apis/rulesApi";
import type { RuleBook } from "@/models/Rules";
import { ChipGroup } from "./ChipGroup";

const SEARCH_DEBOUNCE_MS = 300;
const MIN_QUERY_LENGTH = 2;

interface RuleFilterProps {
  /** Called with the new search, or null when the search box is empty (or too short). */
  onApply?: (request: RuleFilterRequest | null) => void;
  /** The currently applied search, so the filter keeps its state when switching compendium tabs. */
  initialFilters?: RuleFilterRequest | null;
}

/** Combines the search text and book into a request, or null when there is nothing to search for. */
function buildRequest(search: string, bookId: string | null): RuleFilterRequest | null {
  const query = search.trim();
  return query.length >= MIN_QUERY_LENGTH ? { query, bookId } : null;
}

/**
 * Search bar plus a filter sheet for rules text. Typing searches after a short pause (or immediately on
 * Enter); the sheet limits the search to one book. Supports "quoted phrases" and -excluded words.
 */
export function RuleFilter({ onApply, initialFilters = null }: RuleFilterProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState(initialFilters?.query ?? "");
  const [bookId, setBookId] = useState<string | null>(initialFilters?.bookId ?? null);
  const [draftBookId, setDraftBookId] = useState<string | null>(bookId);
  const [books, setBooks] = useState<RuleBook[]>([]);
  const bookIdRef = useRef(bookId);
  const lastEmittedRef = useRef(JSON.stringify(initialFilters));
  const debounceRef = useRef<number | undefined>(undefined);

  /** Sends a request upstream, skipping it when it is identical to the last one sent. */
  const emit = (searchText: string, book: string | null) => {
    const request = buildRequest(searchText, book);
    const serialized = JSON.stringify(request);
    if (serialized === lastEmittedRef.current) return;
    lastEmittedRef.current = serialized;
    onApply?.(request);
  };
  const emitRef = useRef(emit);

  /** Keeps refs pointing at the latest values so the debounced search uses the current book. */
  useEffect(() => {
    bookIdRef.current = bookId;
    emitRef.current = emit;
  });

  /** Loads the books for the filter sheet. */
  useEffect(() => {
    const controller = new AbortController();
    getRuleBooks(controller.signal).then(setBooks).catch(() => {
      // The sheet just shows "All books" if the list cannot be loaded.
    });
    return () => controller.abort();
  }, []);

  /** Debounces the search so results update as the user types without a request per keystroke. */
  useEffect(() => {
    window.clearTimeout(debounceRef.current);
    debounceRef.current = window.setTimeout(() => {
      emitRef.current(search, bookIdRef.current);
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(debounceRef.current);
  }, [search]);

  /** Closes the filter sheet on Escape and locks background scrolling while it is open. */
  useEffect(() => {
    if (!menuOpen) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMenuOpen(false);
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [menuOpen]);

  /** Runs the search immediately when Enter/Go is pressed (also dismisses the mobile keyboard). */
  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    window.clearTimeout(debounceRef.current);
    emit(search, bookId);
    (document.activeElement as HTMLElement | null)?.blur();
  };

  /** Clears the search text and the results. */
  const clearSearch = () => {
    setSearch("");
    window.clearTimeout(debounceRef.current);
    emit("", bookId);
  };

  /** Opens the filter sheet pre-filled with the book that is currently applied. */
  const openMenu = () => {
    setDraftBookId(bookId);
    setMenuOpen(true);
  };

  /** Applies the chosen book together with the current search and closes the sheet. */
  const applyFilters = (event?: FormEvent<HTMLFormElement>) => {
    event?.preventDefault();
    setBookId(draftBookId);
    emit(search, draftBookId);
    setMenuOpen(false);
  };

  const bookTitles = new Map(books.map((book) => [book.id, book.title]));
  const activeCount = bookId ? 1 : 0;

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2">
        <form role="search" onSubmit={submitSearch} className="flex min-w-0 flex-1 items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 shadow-sm">
          <Search className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
          <input
            type="search"
            enterKeyHint="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search rules..."
            aria-label="Search rules text"
            className="w-full min-w-0 border-0 bg-transparent text-base text-slate-700 placeholder:text-slate-400 focus:outline-none sm:text-sm [&::-webkit-search-cancel-button]:hidden"
          />
          {search && (
            <button
              type="button"
              onClick={clearSearch}
              aria-label="Clear search"
              className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-slate-500 hover:bg-slate-200"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </form>

        <button
          type="button"
          onClick={openMenu}
          className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600 shadow-sm transition hover:bg-slate-100"
          aria-label={activeCount > 0 ? `Open rule filters (${activeCount} active)` : "Open rule filters"}
        >
          <SlidersHorizontal className="h-4 w-4" />
          {activeCount > 0 && (
            <span className="absolute -right-1.5 -top-1.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-violet-600 px-1 text-[10px] font-semibold text-white">
              {activeCount}
            </span>
          )}
        </button>
      </div>

      {bookId && bookTitles.has(bookId) && (
        <p className="text-xs text-slate-500">In {bookTitles.get(bookId)}</p>
      )}

      {/* Portaled to <body> so ancestors with backdrop-filter/transform cannot trap the fixed overlay. */}
      {menuOpen && createPortal(
        <div
          className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/60 sm:items-center sm:p-4"
          onClick={() => setMenuOpen(false)}
        >
          <form
            role="dialog"
            aria-modal="true"
            aria-labelledby="rule-filter-title"
            onSubmit={applyFilters}
            className="flex h-[85dvh] w-full sm:h-auto sm:max-h-[90dvh] max-w-xl flex-col rounded-t-2xl border border-slate-200 bg-white shadow-2xl sm:rounded-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex shrink-0 items-center justify-between gap-3 border-b border-slate-100 p-4">
              <h3 id="rule-filter-title" className="text-lg font-semibold text-slate-900">Rule Filters</h3>
              <button
                type="button"
                onClick={() => setMenuOpen(false)}
                className="flex h-10 w-10 items-center justify-center rounded-md border border-slate-200 text-slate-600 hover:bg-slate-100"
                aria-label="Close filter modal"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="min-h-0 flex-1 space-y-5 overflow-y-auto overscroll-contain p-4">
              <ChipGroup
                label="Book"
                options={books.map((book) => book.id)}
                selected={draftBookId ? [draftBookId] : []}
                format={(id) => bookTitles.get(id) ?? id}
                onToggle={(id) => setDraftBookId((current) => (current === id ? null : id))}
              />
              <p className="text-xs text-slate-500">
                Pick a book to search only its chapters, or none to search every book.
                Use "quotes" for an exact phrase and -word to leave a word out.
              </p>
            </div>

            <div className="flex shrink-0 items-center justify-end gap-2 border-t border-slate-100 p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
              <button
                type="button"
                onClick={() => setDraftBookId(null)}
                className="rounded-lg border border-slate-200 px-4 py-2.5 text-sm text-slate-700 hover:bg-slate-100"
              >
                Clear
              </button>
              <button
                type="submit"
                className="rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700"
              >
                Apply
              </button>
            </div>
          </form>
        </div>,
        document.body,
      )}
    </div>
  );
}

export default RuleFilter;
