import { SlidersHorizontal, Search, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import type { FormEvent } from "react";
import type { Operator, TraitFilterRequest } from "@/apis/traitApi";
import type { TraitCategory } from "@/models/Trait";
import { formatTraitCategory } from "../traits/traitFormatters";

const traitCategoryOptions: TraitCategory[] = [
  "BUFF",
  "CONTROL",
  "CRAFTING_ITEMS",
  "DAMAGE",
  "DEBUFF",
  "DECLARED",
  "DEFENSE",
  "ENVIRONMENTAL",
  "HEALING_RECOVERY",
  "MAGIC",
  "MOBILITY",
  "OTHER",
  "PASSIVE",
  "RACIAL",
  "RESOURCE_ECONOMY",
  "SKILL",
  "SOCIAL_ROLEPLAY",
  "STATUS_CONDITIONS",
  "STEALTH_DECEPTION",
  "SUMMONING_COMPANIONS",
  "TRANSFORMATION",
  "TRIGGERED",
  "UTILITY_EXPLORATION",
  "WEAPON",
];

const SEARCH_DEBOUNCE_MS = 300;

type NumericKey = "lvl" | "purchaseCost" | "tpCost" | "mpCost" | "epCost";
type OperatorKey = "lvlOperator" | "purchaseOperator" | "tpOperator" | "mpOperator" | "epOperator";

/** Every numeric filter row shown in the modal, grouped so they can be rendered in sections. */
const numericFields: Array<{ label: string; key: NumericKey; opKey: OperatorKey; group: "requirements" | "activation" }> = [
  { label: "Level", key: "lvl", opKey: "lvlOperator", group: "requirements" },
  { label: "Purchase", key: "purchaseCost", opKey: "purchaseOperator", group: "requirements" },
  { label: "MP", key: "mpCost", opKey: "mpOperator", group: "activation" },
  { label: "TP", key: "tpCost", opKey: "tpOperator", group: "activation" },
  { label: "EP", key: "epCost", opKey: "epOperator", group: "activation" },
];

/** The filters edited inside the modal (everything except the name search). */
interface TraitFilterState {
  categories: TraitCategory[];
  matchAll: boolean;
  lvl: number | "";
  lvlOperator: Operator;
  purchaseCost: number | "";
  purchaseOperator: Operator;
  tpCost: number | "";
  tpOperator: Operator;
  mpCost: number | "";
  mpOperator: Operator;
  epCost: number | "";
  epOperator: Operator;
}

interface TraitFilterProps {
  /** Called with the new request, or null when no filters are active (show every trait). */
  onApply?: (request: TraitFilterRequest | null) => void;
  /** The currently applied request, so the filter keeps its state when switching compendium tabs. */
  initialFilters?: TraitFilterRequest | null;
}

const initialState: TraitFilterState = {
  categories: [],
  matchAll: false,
  lvl: "",
  lvlOperator: "lte",
  purchaseCost: "",
  purchaseOperator: "lte",
  tpCost: "",
  tpOperator: "lte",
  mpCost: "",
  mpOperator: "lte",
  epCost: "",
  epOperator: "lte",
};

/** Rebuilds modal state from a previously applied request. */
function stateFromRequest(request: TraitFilterRequest | null | undefined): TraitFilterState {
  if (!request) return initialState;
  return {
    categories: request.categories,
    matchAll: request.matchAll,
    lvl: request.lvl ?? "",
    lvlOperator: request.lvlOperator,
    purchaseCost: request.purchaseCost ?? "",
    purchaseOperator: request.purchaseOperator,
    tpCost: request.tpCost ?? "",
    tpOperator: request.tpOperator,
    mpCost: request.mpCost ?? "",
    mpOperator: request.mpOperator,
    epCost: request.epCost ?? "",
    epOperator: request.epOperator,
  };
}

/** Counts how many modal filters are set, for the badge on the filter button. */
function countActiveFilters(state: TraitFilterState): number {
  return (state.categories.length > 0 ? 1 : 0) + numericFields.filter(({ key }) => state[key] !== "").length;
}

/** Combines modal state and search text into an API request, or null when nothing is filtered. */
function buildRequest(state: TraitFilterState, search: string): TraitFilterRequest | null {
  const name = search.trim();
  if (countActiveFilters(state) === 0 && name === "") return null;

  return {
    categories: state.categories,
    matchAll: state.matchAll,
    page: 0,
    size: 5,
    lvl: state.lvl === "" ? null : state.lvl,
    lvlOperator: state.lvlOperator,
    purchaseCost: state.purchaseCost === "" ? null : state.purchaseCost,
    purchaseOperator: state.purchaseOperator,
    tpCost: state.tpCost === "" ? null : state.tpCost,
    tpOperator: state.tpOperator,
    mpCost: state.mpCost === "" ? null : state.mpCost,
    mpOperator: state.mpOperator,
    epCost: state.epCost === "" ? null : state.epCost,
    epOperator: state.epOperator,
    name: name || null,
  };
}

/**
 * Search bar plus a filter sheet for traits.
 * Typing searches automatically after a short pause (or immediately on Enter); the sheet holds
 * categories, level/purchase requirements and activation costs, which apply when "Apply" is tapped.
 */
export function TraitFilter({ onApply, initialFilters = null }: TraitFilterProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState(initialFilters?.name ?? "");
  const [applied, setApplied] = useState<TraitFilterState>(() => stateFromRequest(initialFilters));
  const [draft, setDraft] = useState<TraitFilterState>(applied);
  const appliedRef = useRef(applied);
  const lastEmittedRef = useRef(JSON.stringify(initialFilters));
  const debounceRef = useRef<number | undefined>(undefined);

  const activeCount = countActiveFilters(applied);

  /** Sends a request upstream, skipping it when it is identical to the last one sent. */
  const emit = (state: TraitFilterState, searchText: string) => {
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
  const toggleCategory = (category: TraitCategory) => {
    setDraft((current) => ({
      ...current,
      categories: current.categories.includes(category)
        ? current.categories.filter((item) => item !== category)
        : [...current.categories, category],
    }));
  };

  /** Updates one numeric draft field; an empty input clears that filter. */
  const updateNumericField = (field: NumericKey, value: string) => {
    setDraft((current) => ({
      ...current,
      [field]: value === "" ? "" : Math.max(0, Number(value)),
    }));
  };

  /** Updates the comparison operator for one numeric draft field. */
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

  /** Renders one "label / operator / value" row for a numeric filter. */
  const renderNumericRow = ({ label, key, opKey }: (typeof numericFields)[number]) => (
    <div key={key} className="grid grid-cols-[72px_64px_minmax(0,1fr)] items-center gap-2">
      <label htmlFor={`trait-filter-${key}`} className="text-sm text-slate-700">{label}</label>

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
        id={`trait-filter-${key}`}
        type="number"
        inputMode="numeric"
        min="0"
        value={draft[key]}
        onChange={(event) => updateNumericField(key, event.target.value)}
        placeholder="Any"
        className="min-w-0 rounded-lg border border-slate-200 bg-slate-50 px-2 py-2 text-base text-slate-700 outline-none sm:text-sm"
      />
    </div>
  );

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
            placeholder="Search traits..."
            aria-label="Search traits by name"
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
          aria-label={activeCount > 0 ? `Open trait filters (${activeCount} active)` : "Open trait filters"}
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
            aria-labelledby="trait-filter-title"
            className="flex max-h-[90dvh] w-full max-w-xl flex-col rounded-t-2xl border border-slate-200 bg-white shadow-2xl sm:rounded-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex shrink-0 items-center justify-between gap-3 border-b border-slate-100 p-4">
              <h3 id="trait-filter-title" className="text-lg font-semibold text-slate-900">Trait Filters</h3>
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
                <div className="flex flex-wrap gap-2 rounded-xl sm:max-h-48 sm:overflow-y-auto border border-slate-200 bg-slate-50 p-2">
                  {traitCategoryOptions.map((category) => {
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
                        {formatTraitCategory(category)}
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
                <span className="block text-sm font-medium text-slate-700">Requirements</span>
                {numericFields.filter((field) => field.group === "requirements").map(renderNumericRow)}
              </div>

              <div className="space-y-3">
                <span className="block text-sm font-medium text-slate-700">Activation Costs</span>
                {numericFields.filter((field) => field.group === "activation").map(renderNumericRow)}
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

export default TraitFilter;
