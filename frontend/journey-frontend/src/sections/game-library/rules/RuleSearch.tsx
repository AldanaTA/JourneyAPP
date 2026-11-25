import { useEffect, useRef, useState } from "react";
import { Search, X } from "lucide-react";
import { searchRules } from "../../../apis/rulesApi";
import type { RuleSearchResult } from "../../../models/Rules";
import { Spinner } from "@/components/ui/spinner";
import { HighlightedSnippet } from "./HighlightedSnippet";

interface RuleSearchProps {
  /** Limits the search to one book. */
  bookId: string;
  onSelect: (result: RuleSearchResult) => void;
}

const MIN_QUERY_LENGTH = 2;
const DEBOUNCE_MS = 300;

/**
 * Search box for a rulebook. Results appear under the box while typing (debounced) and close on selection,
 * Escape, or a click outside.
 */
export function RuleSearch({ bookId, onSelect }: RuleSearchProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<RuleSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const trimmed = query.trim();
  const active = trimmed.length >= MIN_QUERY_LENGTH;

  /** Runs the search a moment after the user stops typing, cancelling any request still in flight. */
  useEffect(() => {
    if (!active) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setLoading(true);
      setError(null);
      searchRules(trimmed, bookId, 20, controller.signal)
        .then((found) => {
          setResults(found);
          setOpen(true);
        })
        .catch((reason: unknown) => {
          if (controller.signal.aborted) return;
          setError(reason instanceof Error ? reason.message : "Search failed");
          setOpen(true);
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }, DEBOUNCE_MS);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [active, trimmed, bookId]);

  /** Closes the results when the user clicks anywhere else. */
  useEffect(() => {
    if (!open) return;
    const handlePointerDown = (event: PointerEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [open]);

  const select = (result: RuleSearchResult) => {
    setOpen(false);
    onSelect(result);
  };

  const showResults = open && active;

  return (
    <div ref={containerRef} className="relative">
      <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 shadow-sm focus-within:border-violet-400 focus-within:ring-2 focus-within:ring-violet-100">
        <Search size={16} className="shrink-0 text-slate-400" aria-hidden="true" />
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onFocus={() => active && setOpen(true)}
          onKeyDown={(event) => {
            if (event.key === "Escape") setOpen(false);
            if (event.key === "Enter" && results.length > 0) select(results[0]);
          }}
          placeholder="Search this book…"
          aria-label="Search this book"
          className="h-10 min-w-0 flex-1 bg-transparent text-sm text-slate-900 outline-none placeholder:text-slate-400 [&::-webkit-search-cancel-button]:hidden"
        />
        {loading && <Spinner className="h-4 w-4 text-slate-400" />}
        {query && !loading && (
          <button
            type="button"
            onClick={() => {
              setQuery("");
              setResults([]);
              setOpen(false);
            }}
            aria-label="Clear search"
            className="rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          >
            <X size={14} />
          </button>
        )}
      </div>

      {showResults && (
        <div className="absolute inset-x-0 top-full z-30 mt-2 max-h-[60vh] overflow-y-auto overscroll-contain rounded-xl border border-slate-200 bg-white p-1 shadow-xl">
          {error ? (
            <p className="p-3 text-sm text-red-700">{error}</p>
          ) : results.length === 0 ? (
            <p className="p-3 text-sm text-slate-500">No rules match “{trimmed}”.</p>
          ) : (
            <ul role="listbox" aria-label="Search results">
              {results.map((result) => (
                <li key={result.sectionId}>
                  <button
                    type="button"
                    onClick={() => select(result)}
                    className="block w-full rounded-lg px-3 py-2 text-left hover:bg-violet-50 focus-visible:bg-violet-50 focus-visible:outline-none"
                  >
                    <span className="block text-[10px] font-bold uppercase tracking-[0.16em] text-violet-600">
                      {result.chapterTitle}
                    </span>
                    <span className="block text-sm font-semibold text-slate-900">{result.sectionTitle}</span>
                    {result.snippet && (
                      <span className="mt-0.5 line-clamp-2 block text-xs leading-5 text-slate-600">
                        <HighlightedSnippet text={result.snippet} />
                      </span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
