import { SlidersHorizontal, Search, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import type { FormEvent } from "react";
import type { JobFilterRequest } from "@/apis/jobApi";
import type { JobCategory } from "@/models/Job";

const jobCategoryOptions: JobCategory[] = [
  "Caster",
  "Companion",
  "Crafter",
  "Defender",
  "Divine",
  "Generalist",
  "Half-Caster",
  "Martial",
  "Nature",
  "Occult",
  "Ranged",
  "Saboteur",
  "Scout",
  "Social",
  "Specialist",
  "Support",
];

const SEARCH_DEBOUNCE_MS = 300;

/**
 * How selected categories are matched. "primary" compares against each job's primary category only;
 * it is exclusive with "all" because a job has a single primary category.
 */
type CategoryMatchMode = "any" | "all" | "primary";

const matchModeOptions: Array<{ value: CategoryMatchMode; label: string }> = [
  { value: "any", label: "Any" },
  { value: "all", label: "All" },
  { value: "primary", label: "Primary" },
];

/** The filters edited inside the modal (everything except the name search). */
interface JobFilterState {
  categories: JobCategory[];
  matchMode: CategoryMatchMode;
  traitName: string;
}

interface JobFilterProps {
  /** Called with the new request, or null when no filters are active (show every job). */
  onApply?: (request: JobFilterRequest | null) => void;
  /** The currently applied request, so the filter keeps its state when switching compendium tabs. */
  initialFilters?: JobFilterRequest | null;
}

const initialState: JobFilterState = {
  categories: [],
  matchMode: "any",
  traitName: "",
};

/** Rebuilds modal state from a previously applied request. */
function stateFromRequest(request: JobFilterRequest | null | undefined): JobFilterState {
  if (!request) return initialState;
  return {
    categories: request.categories,
    matchMode: request.primaryOnly ? "primary" : request.matchAll ? "all" : "any",
    traitName: request.traitName ?? "",
  };
}

/** Counts how many modal filters are set, for the badge on the filter button. */
function countActiveFilters(state: JobFilterState): number {
  return (state.categories.length > 0 ? 1 : 0) + (state.traitName.trim() !== "" ? 1 : 0);
}

/** Combines modal state and search text into an API request, or null when nothing is filtered. */
function buildRequest(state: JobFilterState, search: string): JobFilterRequest | null {
  const name = search.trim();
  if (countActiveFilters(state) === 0 && name === "") return null;

  return {
    categories: state.categories,
    matchAll: state.matchMode === "all",
    primaryOnly: state.matchMode === "primary",
    page: 0,
    size: 5,
    name: name || null,
    traitName: state.traitName.trim() || null,
  };
}

/**
 * Search bar plus a filter sheet for jobs.
 * Typing searches automatically after a short pause (or immediately on Enter); the sheet holds
 * categories and a "contains trait" search, which apply when "Apply" is tapped.
 */
export function JobFilter({ onApply, initialFilters = null }: JobFilterProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState(initialFilters?.name ?? "");
  const [applied, setApplied] = useState<JobFilterState>(() => stateFromRequest(initialFilters));
  const [draft, setDraft] = useState<JobFilterState>(applied);
  const appliedRef = useRef(applied);
  const lastEmittedRef = useRef(JSON.stringify(initialFilters));
  const debounceRef = useRef<number | undefined>(undefined);

  const activeCount = countActiveFilters(applied);

  /** Sends a request upstream, skipping it when it is identical to the last one sent. */
  const emit = (state: JobFilterState, searchText: string) => {
    const request = buildRequest(state, searchText);
    const serialized = JSON.stringify(request);
    if (serialized === lastEmittedRef.current) return;
    lastEmittedRef.current = serialized;
    onApply?.(request);
  };
  const emitRef = useRef(emit);

  /** Keeps refs pointing at the latest values so the debounced search uses current filters. */
  useEffect(() => {
    appliedRef.current = applied;
    emitRef.current = emit;
  });

  /** Debounces the name search so results update as the user types without a request per keystroke. */
  useEffect(() => {
    window.clearTimeout(debounceRef.current);
    debounceRef.current = window.setTimeout(() => {
      emitRef.current(appliedRef.current, search);
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
    emit(applied, search);
    (document.activeElement as HTMLElement | null)?.blur();
  };

  /** Clears the search text and immediately shows results without the name filter. */
  const clearSearch = () => {
    setSearch("");
    window.clearTimeout(debounceRef.current);
    emit(applied, "");
  };

  /** Opens the filter sheet pre-filled with the filters that are currently applied. */
  const openMenu = () => {
    setDraft(applied);
    setMenuOpen(true);
  };

  /** Adds or removes a category from the draft selection. */
  const toggleCategory = (category: JobCategory) => {
    setDraft((current) => ({
      ...current,
      categories: current.categories.includes(category)
        ? current.categories.filter((item) => item !== category)
        : [...current.categories, category],
    }));
  };

  /** Resets every draft filter in the sheet (applied on "Apply"). */
  const clearFilters = () => {
    setDraft(initialState);
  };

  /** Applies the draft filters together with the current search and closes the sheet. */
  const applyFilters = () => {
    setApplied(draft);
    emit(draft, search);
    setMenuOpen(false);
  };

  /** Applies the filters when Enter is pressed in the trait search box. */
  const submitFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    applyFilters();
  };

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
            placeholder="Search jobs..."
            aria-label="Search jobs by name"
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
          aria-label={activeCount > 0 ? `Open job filters (${activeCount} active)` : "Open job filters"}
        >
          <SlidersHorizontal className="h-4 w-4" />
          {activeCount > 0 && (
            <span className="absolute -right-1.5 -top-1.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-violet-600 px-1 text-[10px] font-semibold text-white">
              {activeCount}
            </span>
          )}
        </button>
      </div>

      {/* Portaled to <body> so ancestors with backdrop-filter/transform cannot trap the fixed overlay. */}
      {menuOpen && createPortal(
        <div
          className="fixed inset-0 z-[70] flex items-end justify-center bg-slate-950/60 sm:items-center sm:p-4"
          onClick={() => setMenuOpen(false)}
        >
          <form
            role="dialog"
            aria-modal="true"
            aria-labelledby="job-filter-title"
            onSubmit={submitFilters}
            className="flex h-[85dvh] w-full sm:h-auto sm:max-h-[90dvh] max-w-xl flex-col rounded-t-2xl border border-slate-200 bg-white shadow-2xl sm:rounded-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex shrink-0 items-center justify-between gap-3 border-b border-slate-100 p-4">
              <h3 id="job-filter-title" className="text-lg font-semibold text-slate-900">Job Filters</h3>
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
              <div>
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-sm font-medium text-slate-700">Categories</span>
                  {draft.categories.length > 0 && (
                    <span className="text-xs text-slate-500">{draft.categories.length} selected</span>
                  )}
                </div>
                <div className="flex flex-wrap gap-2 rounded-xl border border-slate-200 bg-slate-50 p-2">
                  {jobCategoryOptions.map((category) => {
                    const selected = draft.categories.includes(category);
                    return (
                      <button
                        key={category}
                        type="button"
                        aria-pressed={selected}
                        onClick={() => toggleCategory(category)}
                        className={[
                          "rounded-full border px-3 py-1.5 text-[11px] font-medium uppercase tracking-wide transition",
                          selected
                            ? "border-violet-500 bg-violet-100 text-violet-700"
                            : "border-slate-200 bg-white text-slate-600 hover:bg-slate-100",
                        ].join(" ")}
                      >
                        {category}
                      </button>
                    );
                  })}
                </div>

                <div className="mt-3 flex items-center justify-between gap-3">
                  <span id="job-filter-match-label" className="text-sm text-slate-700">Match</span>
                  <div
                    role="radiogroup"
                    aria-labelledby="job-filter-match-label"
                    className="flex rounded-lg border border-slate-200 bg-slate-50 p-0.5"
                  >
                    {matchModeOptions.map(({ value, label }) => {
                      const selected = draft.matchMode === value;
                      return (
                        <button
                          key={value}
                          type="button"
                          role="radio"
                          aria-checked={selected}
                          onClick={() => setDraft((current) => ({ ...current, matchMode: value }))}
                          className={[
                            "rounded-md px-3 py-1.5 text-xs font-medium transition",
                            selected ? "bg-white text-violet-700 shadow-sm" : "text-slate-600 hover:text-slate-900",
                          ].join(" ")}
                        >
                          {label}
                        </button>
                      );
                    })}
                  </div>
                </div>
                <p className="mt-1.5 text-xs text-slate-500">
                  {draft.matchMode === "any" && "Jobs with at least one selected category."}
                  {draft.matchMode === "all" && "Jobs with every selected category."}
                  {draft.matchMode === "primary" && "Jobs whose primary category is one of the selected."}
                </p>
              </div>

              <div className="space-y-2">
                <label htmlFor="job-filter-trait-name" className="block text-sm font-medium text-slate-700">
                  Includes Trait
                </label>
                <input
                  id="job-filter-trait-name"
                  type="text"
                  enterKeyHint="done"
                  value={draft.traitName}
                  onChange={(event) => setDraft((current) => ({ ...current, traitName: event.target.value }))}
                  placeholder="Trait name, e.g. Flair"
                  className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-base text-slate-700 outline-none placeholder:text-slate-400 sm:text-sm"
                />
              </div>
            </div>

            <div className="flex shrink-0 items-center justify-end gap-2 border-t border-slate-100 p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
              <button
                type="button"
                onClick={clearFilters}
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

export default JobFilter;
