import { SlidersHorizontal, Search, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import type { FormEvent } from "react";
import type { RaceFilterRequest } from "@/apis/raceApi";
import type { Operator } from "@/apis/traitApi";
import type { RaceSize } from "@/models/Race";
import { RACE_SIZES, formatRaceSize } from "../races/raceFormatters";

const SEARCH_DEBOUNCE_MS = 300;

/** The filters edited inside the modal (everything except the name search). */
interface RaceFilterState {
  sizes: RaceSize[];
  movement: number | "";
  movementOperator: Operator;
  traitName: string;
}

interface RaceFilterProps {
  /** Called with the new request, or null when no filters are active (show every race). */
  onApply?: (request: RaceFilterRequest | null) => void;
  /** The currently applied request, so the filter keeps its state when switching compendium tabs. */
  initialFilters?: RaceFilterRequest | null;
}

const initialState: RaceFilterState = {
  sizes: [],
  movement: "",
  movementOperator: "gte",
  traitName: "",
};

/** Rebuilds modal state from a previously applied request. */
function stateFromRequest(request: RaceFilterRequest | null | undefined): RaceFilterState {
  if (!request) return initialState;
  return {
    sizes: request.sizes,
    movement: request.movement ?? "",
    movementOperator: request.movementOperator,
    traitName: request.traitName ?? "",
  };
}

/** Counts how many modal filters are set, for the badge on the filter button. */
function countActiveFilters(state: RaceFilterState): number {
  return (
    (state.sizes.length > 0 ? 1 : 0) +
    (state.movement !== "" ? 1 : 0) +
    (state.traitName.trim() !== "" ? 1 : 0)
  );
}

/** Combines modal state and search text into an API request, or null when nothing is filtered. */
function buildRequest(state: RaceFilterState, search: string): RaceFilterRequest | null {
  const name = search.trim();
  if (countActiveFilters(state) === 0 && name === "") return null;

  return {
    sizes: state.sizes,
    movement: state.movement === "" ? null : state.movement,
    movementOperator: state.movementOperator,
    page: 0,
    size: 5,
    name: name || null,
    traitName: state.traitName.trim() || null,
  };
}

/**
 * Search bar plus a filter sheet for races.
 * Typing searches automatically after a short pause (or immediately on Enter); the sheet holds
 * size, movement and a "contains trait" search, which apply when "Apply" is tapped.
 */
export function RaceFilter({ onApply, initialFilters = null }: RaceFilterProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState(initialFilters?.name ?? "");
  const [applied, setApplied] = useState<RaceFilterState>(() => stateFromRequest(initialFilters));
  const [draft, setDraft] = useState<RaceFilterState>(applied);
  const appliedRef = useRef(applied);
  const lastEmittedRef = useRef(JSON.stringify(initialFilters));
  const debounceRef = useRef<number | undefined>(undefined);

  const activeCount = countActiveFilters(applied);

  /** Sends a request upstream, skipping it when it is identical to the last one sent. */
  const emit = (state: RaceFilterState, searchText: string) => {
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

  /** Adds or removes a size from the draft selection. */
  const toggleSize = (size: RaceSize) => {
    setDraft((current) => ({
      ...current,
      sizes: current.sizes.includes(size)
        ? current.sizes.filter((item) => item !== size)
        : [...current.sizes, size],
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

  /** Applies the filters when Enter is pressed in a text box inside the sheet. */
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
            placeholder="Search races..."
            aria-label="Search races by name"
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
          aria-label={activeCount > 0 ? `Open race filters (${activeCount} active)` : "Open race filters"}
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
            aria-labelledby="race-filter-title"
            onSubmit={submitFilters}
            className="flex h-[85dvh] w-full sm:h-auto sm:max-h-[90dvh] max-w-xl flex-col rounded-t-2xl border border-slate-200 bg-white shadow-2xl sm:rounded-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex shrink-0 items-center justify-between gap-3 border-b border-slate-100 p-4">
              <h3 id="race-filter-title" className="text-lg font-semibold text-slate-900">Race Filters</h3>
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
                  <span className="text-sm font-medium text-slate-700">Size</span>
                  {draft.sizes.length > 0 && (
                    <span className="text-xs text-slate-500">{draft.sizes.length} selected</span>
                  )}
                </div>
                <div className="flex flex-wrap gap-2 rounded-xl border border-slate-200 bg-slate-50 p-2">
                  {RACE_SIZES.map((size) => {
                    const selected = draft.sizes.includes(size);
                    return (
                      <button
                        key={size}
                        type="button"
                        aria-pressed={selected}
                        onClick={() => toggleSize(size)}
                        className={[
                          "rounded-full border px-3 py-1.5 text-[11px] font-medium uppercase tracking-wide transition",
                          selected
                            ? "border-violet-500 bg-violet-100 text-violet-700"
                            : "border-slate-200 bg-white text-slate-600 hover:bg-slate-100",
                        ].join(" ")}
                      >
                        {formatRaceSize(size)}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="space-y-3">
                <span className="block text-sm font-medium text-slate-700">Movement (ft)</span>
                <div className="grid grid-cols-[72px_64px_minmax(0,1fr)] items-center gap-2">
                  <label htmlFor="race-filter-movement" className="text-sm text-slate-700">Speed</label>

                  <select
                    value={draft.movementOperator}
                    onChange={(event) =>
                      setDraft((current) => ({ ...current, movementOperator: event.target.value as Operator }))
                    }
                    aria-label="Movement comparison"
                    className="rounded-lg border border-slate-200 bg-slate-50 px-2 py-2 text-base text-slate-700 outline-none sm:text-sm"
                  >
                    <option value="lte">≤</option>
                    <option value="lt">&lt;</option>
                    <option value="eq">=</option>
                    <option value="gt">&gt;</option>
                    <option value="gte">≥</option>
                  </select>

                  <input
                    id="race-filter-movement"
                    type="number"
                    inputMode="numeric"
                    min="0"
                    step="5"
                    value={draft.movement}
                    onChange={(event) =>
                      setDraft((current) => ({
                        ...current,
                        movement: event.target.value === "" ? "" : Math.max(0, Number(event.target.value)),
                      }))
                    }
                    placeholder="Any"
                    className="min-w-0 rounded-lg border border-slate-200 bg-slate-50 px-2 py-2 text-base text-slate-700 outline-none sm:text-sm"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <label htmlFor="race-filter-trait-name" className="block text-sm font-medium text-slate-700">
                  Includes Trait
                </label>
                <input
                  id="race-filter-trait-name"
                  type="text"
                  enterKeyHint="done"
                  value={draft.traitName}
                  onChange={(event) => setDraft((current) => ({ ...current, traitName: event.target.value }))}
                  placeholder="Trait name, e.g. Darkvision"
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

export default RaceFilter;
