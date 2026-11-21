import { SlidersHorizontal, Search, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import type { FormEvent } from "react";
import type { Operator, PowerFilterRequest } from "@/apis/powerApi";
import type { PowerCategory, PowerType } from "@/models/Power";
import { formatPowerCategory } from "../powers/powerFormatters";

const powerTypeOptions: PowerType[] = ["Destruction", "Support", "Sabotage", "Utility"];
const powerCategoryOptions: PowerCategory[] = [
  "ACID", "BLEEDING", "BLESSED", "BUFF", "BLINDED", "BLUDGEONING", "CHARMED", "COLD", "CONTROL", "CURSED",
  "DAMAGE", "DARK", "DAZED", "DEAFENED", "DEBUFF", "DECLARED", "DEFENSE", "ENFEEBLE", "EXHAUSTION", "FIRE",
  "FORCE", "FRIGHTENED", "HEALING_RECOVERY", "IMPAIRED", "INCAPACITATED", "INFATUATED", "INSPIRED", "INVISIBLE",
  "LIGHT", "LIGHTNING", "LIGHT_ATTACK", "MAGIC", "MIGHTY", "MOBILITY", "PETRIFIED", "PIERCING", "POISON", "PRONE",
  "PSYCHIC", "RESTRAINED", "RUSH", "SILENCED", "SLASHING", "SLUGGISH", "STUNNED", "SUPPRESSED", "STRONG_ATTACK",
  "SUMMONING_COMPANIONS", "TRIGGERED", "UNCONSCIOUS", "VULNERABLE",
];

const SEARCH_DEBOUNCE_MS = 300;

type CostKey = "tpCost" | "hpCost" | "mpCost" | "epCost";
type OperatorKey = "tpOperator" | "hpOperator" | "mpOperator" | "epOperator";

/** Every resource cost row shown in the filter sheet. */
const costFields: Array<{ label: string; key: CostKey; opKey: OperatorKey }> = [
  { label: "TP", key: "tpCost", opKey: "tpOperator" },
  { label: "HP", key: "hpCost", opKey: "hpOperator" },
  { label: "MP", key: "mpCost", opKey: "mpOperator" },
  { label: "EP", key: "epCost", opKey: "epOperator" },
];

/** The filters edited inside the sheet (everything except the name search). */
interface PowerFilterState {
  type: PowerType | "";
  categories: PowerCategory[];
  matchAll: boolean;
  tpCost: number | "";
  tpOperator: Operator;
  hpCost: number | "";
  hpOperator: Operator;
  mpCost: number | "";
  mpOperator: Operator;
  epCost: number | "";
  epOperator: Operator;
}

interface PowerFilterProps {
  /** Called with the new request, or null when no filters are active (show every power). */
  onApply?: (request: PowerFilterRequest | null) => void;
  /** The currently applied request, so the filter keeps its state when switching compendium tabs. */
  initialFilters?: PowerFilterRequest | null;
}

const initialState: PowerFilterState = {
  type: "",
  categories: [],
  matchAll: false,
  tpCost: "",
  tpOperator: "lte",
  hpCost: "",
  hpOperator: "lte",
  mpCost: "",
  mpOperator: "lte",
  epCost: "",
  epOperator: "lte",
};

/** Rebuilds sheet state from a previously applied request. */
function stateFromRequest(request: PowerFilterRequest | null | undefined): PowerFilterState {
  if (!request) return initialState;
  return {
    type: request.type ?? "",
    categories: request.categories,
    matchAll: request.matchAll,
    tpCost: request.tpCost ?? "",
    tpOperator: request.tpOperator,
    hpCost: request.hpCost ?? "",
    hpOperator: request.hpOperator,
    mpCost: request.mpCost ?? "",
    mpOperator: request.mpOperator,
    epCost: request.epCost ?? "",
    epOperator: request.epOperator,
  };
}

/** Counts how many sheet filters are set, for the badge on the filter button. */
function countActiveFilters(state: PowerFilterState): number {
  return (state.type ? 1 : 0)
    + (state.categories.length > 0 ? 1 : 0)
    + costFields.filter(({ key }) => state[key] !== "").length;
}

/** Combines sheet state and search text into an API request, or null when nothing is filtered. */
function buildRequest(state: PowerFilterState, search: string): PowerFilterRequest | null {
  const name = search.trim();
  if (countActiveFilters(state) === 0 && name === "") return null;

  return {
    type: state.type || null,
    categories: state.categories,
    matchAll: state.matchAll,
    page: 0,
    size: 5,
    tpCost: state.tpCost === "" ? null : state.tpCost,
    tpOperator: state.tpOperator,
    hpCost: state.hpCost === "" ? null : state.hpCost,
    hpOperator: state.hpOperator,
    mpCost: state.mpCost === "" ? null : state.mpCost,
    mpOperator: state.mpOperator,
    epCost: state.epCost === "" ? null : state.epCost,
    epOperator: state.epOperator,
    name: name || null,
  };
}

/**
 * Search bar plus a filter sheet for powers.
 * Typing searches automatically after a short pause (or immediately on Enter); the sheet holds
 * power type, categories and resource costs, which apply when "Apply" is tapped.
 */
export function PowerFilter({ onApply, initialFilters = null }: PowerFilterProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState(initialFilters?.name ?? "");
  const [applied, setApplied] = useState<PowerFilterState>(() => stateFromRequest(initialFilters));
  const [draft, setDraft] = useState<PowerFilterState>(applied);
  const appliedRef = useRef(applied);
  const lastEmittedRef = useRef(JSON.stringify(initialFilters));
  const debounceRef = useRef<number | undefined>(undefined);

  const activeCount = countActiveFilters(applied);

  /** Sends a request upstream, skipping it when it is identical to the last one sent. */
  const emit = (state: PowerFilterState, searchText: string) => {
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
  const toggleCategory = (category: PowerCategory) => {
    setDraft((current) => ({
      ...current,
      categories: current.categories.includes(category)
        ? current.categories.filter((item) => item !== category)
        : [...current.categories, category],
    }));
  };

  /** Updates one cost draft field; an empty input clears that filter. */
  const updateCostField = (field: CostKey, value: string) => {
    setDraft((current) => ({
      ...current,
      [field]: value === "" ? "" : Math.max(0, Number(value)),
    }));
  };

  /** Updates the comparison operator for one cost draft field. */
  const updateOperator = (field: OperatorKey, value: Operator) => {
    setDraft((current) => ({ ...current, [field]: value }));
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

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white p-2 shadow-sm">
        <form role="search" onSubmit={submitSearch} className="flex min-w-0 flex-1 items-center gap-2 rounded-lg bg-slate-100 px-3 py-2">
          <Search className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
          <input
            type="search"
            enterKeyHint="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search powers..."
            aria-label="Search powers by name"
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
          className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-slate-100 text-slate-600 transition hover:bg-slate-200"
          aria-label={activeCount > 0 ? `Open power filters (${activeCount} active)` : "Open power filters"}
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
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="power-filter-title"
            className="flex max-h-[90dvh] w-full max-w-xl flex-col rounded-t-2xl border border-slate-200 bg-white shadow-2xl sm:rounded-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex shrink-0 items-center justify-between gap-3 border-b border-slate-100 p-4">
              <h3 id="power-filter-title" className="text-lg font-semibold text-slate-900">Power Filters</h3>
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
                <label htmlFor="power-type-filter" className="mb-2 block text-sm font-medium text-slate-700">
                  Power Type
                </label>
                <select
                  id="power-type-filter"
                  value={draft.type}
                  onChange={(event) => setDraft((current) => ({ ...current, type: event.target.value as PowerType | "" }))}
                  className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-base text-slate-700 outline-none sm:text-sm"
                >
                  <option value="">All types</option>
                  {powerTypeOptions.map((type) => (
                    <option key={type} value={type}>{type}</option>
                  ))}
                </select>
              </div>

              <div>
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-sm font-medium text-slate-700">Categories</span>
                  {draft.categories.length > 0 && (
                    <span className="text-xs text-slate-500">{draft.categories.length} selected</span>
                  )}
                </div>
                <div className="flex flex-wrap gap-2 rounded-xl sm:max-h-48 sm:overflow-y-auto border border-slate-200 bg-slate-50 p-2">
                  {powerCategoryOptions.map((category) => {
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
                        {formatPowerCategory(category)}
                      </button>
                    );
                  })}
                </div>

                <label className="mt-3 flex items-center gap-2 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    className="h-4 w-4"
                    checked={draft.matchAll}
                    onChange={(event) => setDraft((current) => ({ ...current, matchAll: event.target.checked }))}
                  />
                  Match all selected categories
                </label>
              </div>

              <div className="space-y-3">
                <span className="block text-sm font-medium text-slate-700">Resource Costs</span>
                {costFields.map(({ label, key, opKey }) => (
                  <div key={key} className="grid grid-cols-[72px_64px_minmax(0,1fr)] items-center gap-2">
                    <label htmlFor={`power-filter-${key}`} className="text-sm text-slate-700">{label}</label>

                    <select
                      value={draft[opKey]}
                      onChange={(event) => updateOperator(opKey, event.target.value as Operator)}
                      aria-label={`${label} comparison`}
                      className="rounded-lg border border-slate-200 bg-slate-50 px-2 py-2 text-base text-slate-700 outline-none sm:text-sm"
                    >
                      <option value="lte">≤</option>
                      <option value="lt">&lt;</option>
                      <option value="eq">=</option>
                      <option value="gt">&gt;</option>
                      <option value="gte">≥</option>
                    </select>

                    <input
                      id={`power-filter-${key}`}
                      type="number"
                      inputMode="numeric"
                      min="0"
                      value={draft[key]}
                      onChange={(event) => updateCostField(key, event.target.value)}
                      placeholder="Any"
                      className="min-w-0 rounded-lg border border-slate-200 bg-slate-50 px-2 py-2 text-base text-slate-700 outline-none sm:text-sm"
                    />
                  </div>
                ))}
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
                type="button"
                onClick={applyFilters}
                className="rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700"
              >
                Apply
              </button>
            </div>
          </div>
        </div>,
        document.body,
      )}
    </div>
  );
}

export default PowerFilter;
