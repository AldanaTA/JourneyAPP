import { SlidersHorizontal, Search, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import type { FormEvent } from "react";
import { getCreatureTypes, getStatusConditions } from "@/apis/creatureApi";
import type { CreatureFilterRequest } from "@/apis/creatureApi";
import { getItemReferenceData } from "@/apis/itemApi";
import type { RaceSize } from "@/models/Race";
import { RACE_SIZES, formatRaceSize } from "../races/raceFormatters";
import { ChipGroup } from "./ChipGroup";
import { toggle } from "./filterUtils";

const SEARCH_DEBOUNCE_MS = 300;

/** The filters edited inside the modal (everything except the name search). */
interface CreatureFilterState {
  creatureTypes: string[];
  minThreatLvl: number | "";
  maxThreatLvl: number | "";
  sizes: RaceSize[];
  weakTo: string[];
  resistantTo: string[];
  immuneTo: string[];
}

interface CreatureFilterProps {
  /** Called with the new request, or null when no filters are active (show every creature). */
  onApply?: (request: CreatureFilterRequest | null) => void;
  /** The currently applied request, so the filter keeps its state when switching compendium tabs. */
  initialFilters?: CreatureFilterRequest | null;
}

/** Filter options loaded from the API the first time the sheet opens. */
interface FilterOptions {
  creatureTypes: string[];
  /** Damage types followed by status conditions; creatures can be weak/resistant/immune to either. */
  augmentTargets: string[];
}

const initialState: CreatureFilterState = {
  creatureTypes: [],
  minThreatLvl: "",
  maxThreatLvl: "",
  sizes: [],
  weakTo: [],
  resistantTo: [],
  immuneTo: [],
};

/** Rebuilds modal state from a previously applied request. */
function stateFromRequest(request: CreatureFilterRequest | null | undefined): CreatureFilterState {
  if (!request) return initialState;
  return {
    creatureTypes: request.creatureTypes,
    minThreatLvl: request.minThreatLvl ?? "",
    maxThreatLvl: request.maxThreatLvl ?? "",
    sizes: request.sizes,
    weakTo: request.weakTo,
    resistantTo: request.resistantTo,
    immuneTo: request.immuneTo,
  };
}

/** Counts how many modal filters are set, for the badge on the filter button. */
function countActiveFilters(state: CreatureFilterState): number {
  return (
    (state.creatureTypes.length > 0 ? 1 : 0) +
    (state.minThreatLvl !== "" || state.maxThreatLvl !== "" ? 1 : 0) +
    (state.sizes.length > 0 ? 1 : 0) +
    (state.weakTo.length > 0 ? 1 : 0) +
    (state.resistantTo.length > 0 ? 1 : 0) +
    (state.immuneTo.length > 0 ? 1 : 0)
  );
}

/** Combines modal state and search text into an API request, or null when nothing is filtered. */
function buildRequest(state: CreatureFilterState, search: string): CreatureFilterRequest | null {
  const name = search.trim();
  if (countActiveFilters(state) === 0 && name === "") return null;

  return {
    creatureTypes: state.creatureTypes,
    minThreatLvl: state.minThreatLvl === "" ? null : state.minThreatLvl,
    maxThreatLvl: state.maxThreatLvl === "" ? null : state.maxThreatLvl,
    sizes: state.sizes,
    weakTo: state.weakTo,
    resistantTo: state.resistantTo,
    immuneTo: state.immuneTo,
    page: 0,
    size: 5,
    name: name || null,
  };
}

/**
 * Search bar plus a filter sheet for creatures.
 * Typing searches automatically after a short pause (or immediately on Enter); the sheet holds type,
 * threat level, size, and weakness/resistance/immunity filters, which apply when "Apply" is tapped.
 */
export function CreatureFilter({ onApply, initialFilters = null }: CreatureFilterProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState(initialFilters?.name ?? "");
  const [applied, setApplied] = useState<CreatureFilterState>(() => stateFromRequest(initialFilters));
  const [draft, setDraft] = useState<CreatureFilterState>(applied);
  const [options, setOptions] = useState<FilterOptions | null>(null);
  const [optionsError, setOptionsError] = useState<string | null>(null);
  const appliedRef = useRef(applied);
  const lastEmittedRef = useRef(JSON.stringify(initialFilters));
  const debounceRef = useRef<number | undefined>(undefined);

  const activeCount = countActiveFilters(applied);

  /** Sends a request upstream, skipping it when it is identical to the last one sent. */
  const emit = (state: CreatureFilterState, searchText: string) => {
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

  /** Loads creature types and damage types the first time the sheet opens. */
  useEffect(() => {
    if (!menuOpen || options) return;
    let isMounted = true;
    Promise.all([getCreatureTypes(), getItemReferenceData(), getStatusConditions()])
      .then(([types, reference, conditions]) => {
        if (!isMounted) return;
        // Types are per source, so the same name can appear more than once; filter by name.
        const typeNames = [...new Set(types.map((type) => type.name))].sort((a, b) => a.localeCompare(b));
        setOptions({
          creatureTypes: typeNames,
          augmentTargets: [
            ...reference.damageTypes.map((type) => type.name),
            ...conditions.map((condition) => condition.name),
          ],
        });
        setOptionsError(null);
      })
      .catch((err: unknown) => {
        if (isMounted) setOptionsError(err instanceof Error ? err.message : "Unable to load filter options");
      });
    return () => {
      isMounted = false;
    };
  }, [menuOpen, options]);

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

  /** Resets every draft filter in the sheet (applied on "Apply"). */
  const clearFilters = () => {
    setDraft(initialState);
  };

  /** Applies the draft filters together with the current search and closes the sheet. */
  const applyFilters = (event?: FormEvent<HTMLFormElement>) => {
    event?.preventDefault();
    setApplied(draft);
    emit(draft, search);
    setMenuOpen(false);
  };

  /** Updates one threat level bound; an empty input clears it. */
  const updateThreat = (field: "minThreatLvl" | "maxThreatLvl", value: string) => {
    setDraft((current) => ({ ...current, [field]: value === "" ? "" : Math.max(1, Number(value)) }));
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
            placeholder="Search creatures..."
            aria-label="Search creatures by name"
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
          aria-label={activeCount > 0 ? `Open creature filters (${activeCount} active)` : "Open creature filters"}
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
          className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/60 sm:items-center sm:p-4"
          onClick={() => setMenuOpen(false)}
        >
          <form
            role="dialog"
            aria-modal="true"
            aria-labelledby="creature-filter-title"
            onSubmit={applyFilters}
            className="flex h-[85dvh] w-full sm:h-auto sm:max-h-[90dvh] max-w-xl flex-col rounded-t-2xl border border-slate-200 bg-white shadow-2xl sm:rounded-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex shrink-0 items-center justify-between gap-3 border-b border-slate-100 p-4">
              <h3 id="creature-filter-title" className="text-lg font-semibold text-slate-900">Creature Filters</h3>
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
              {optionsError && (
                <p className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
                  Unable to load filter options: {optionsError}
                </p>
              )}

              {options && (
                <ChipGroup
                  label="Type"
                  options={options.creatureTypes}
                  selected={draft.creatureTypes}
                  onToggle={(value) => setDraft((current) => ({ ...current, creatureTypes: toggle(current.creatureTypes, value) }))}
                />
              )}

              <div className="space-y-2">
                <span className="block text-sm font-medium text-slate-700">Threat Level</span>
                <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-2">
                  <input
                    type="number"
                    inputMode="numeric"
                    min="1"
                    value={draft.minThreatLvl}
                    onChange={(event) => updateThreat("minThreatLvl", event.target.value)}
                    placeholder="Min"
                    aria-label="Minimum threat level"
                    className="min-w-0 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-base text-slate-700 outline-none sm:text-sm"
                  />
                  <span className="text-sm text-slate-500">to</span>
                  <input
                    type="number"
                    inputMode="numeric"
                    min="1"
                    value={draft.maxThreatLvl}
                    onChange={(event) => updateThreat("maxThreatLvl", event.target.value)}
                    placeholder="Max"
                    aria-label="Maximum threat level"
                    className="min-w-0 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-base text-slate-700 outline-none sm:text-sm"
                  />
                </div>
              </div>

              <ChipGroup
                label="Size"
                options={RACE_SIZES}
                selected={draft.sizes}
                format={formatRaceSize}
                onToggle={(value) => setDraft((current) => ({ ...current, sizes: toggle(current.sizes, value) }))}
              />

              {options && (
                <>
                  <p className="text-xs text-slate-500">
                    Damage types are listed first, then status conditions.
                  </p>
                  <ChipGroup
                    label="Weak To"
                    options={options.augmentTargets}
                    selected={draft.weakTo}
                    onToggle={(value) => setDraft((current) => ({ ...current, weakTo: toggle(current.weakTo, value) }))}
                  />
                  <ChipGroup
                    label="Resistant To"
                    options={options.augmentTargets}
                    selected={draft.resistantTo}
                    onToggle={(value) => setDraft((current) => ({ ...current, resistantTo: toggle(current.resistantTo, value) }))}
                  />
                  <ChipGroup
                    label="Immune To"
                    options={options.augmentTargets}
                    selected={draft.immuneTo}
                    onToggle={(value) => setDraft((current) => ({ ...current, immuneTo: toggle(current.immuneTo, value) }))}
                  />
                </>
              )}
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

export default CreatureFilter;
