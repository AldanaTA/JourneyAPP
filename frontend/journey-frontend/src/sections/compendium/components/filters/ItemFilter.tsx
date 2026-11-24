import { SlidersHorizontal, Search, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import type { FormEvent } from "react";
import { getItemReferenceData } from "@/apis/itemApi";
import type { ItemFilterRequest } from "@/apis/itemApi";
import type { ItemRarity, ItemReferenceData, ItemType } from "@/models/Item";
import { ITEM_RARITIES, ITEM_TYPES, formatItemRarity, formatItemType } from "../items/itemFormatters";

const SEARCH_DEBOUNCE_MS = 300;

/** The filters edited inside the modal (everything except the name search). */
interface ItemFilterState {
  itemTypes: ItemType[];
  rarities: ItemRarity[];
  magicOnly: boolean;
  attunementOnly: boolean;
  weaponTypes: string[];
  damageTypes: string[];
}

interface ItemFilterProps {
  /** Called with the new request, or null when no filters are active (show every item). */
  onApply?: (request: ItemFilterRequest | null) => void;
  /** The currently applied request, so the filter keeps its state when switching compendium tabs. */
  initialFilters?: ItemFilterRequest | null;
}

const initialState: ItemFilterState = {
  itemTypes: [],
  rarities: [],
  magicOnly: false,
  attunementOnly: false,
  weaponTypes: [],
  damageTypes: [],
};

/** Rebuilds modal state from a previously applied request. */
function stateFromRequest(request: ItemFilterRequest | null | undefined): ItemFilterState {
  if (!request) return initialState;
  return {
    itemTypes: request.itemTypes,
    rarities: request.rarities,
    magicOnly: request.magicOnly,
    attunementOnly: request.attunementOnly,
    weaponTypes: request.weaponTypes,
    damageTypes: request.damageTypes,
  };
}

/** Counts how many modal filters are set, for the badge on the filter button. */
function countActiveFilters(state: ItemFilterState): number {
  return (
    (state.itemTypes.length > 0 ? 1 : 0) +
    (state.rarities.length > 0 ? 1 : 0) +
    (state.magicOnly ? 1 : 0) +
    (state.attunementOnly ? 1 : 0) +
    (state.weaponTypes.length > 0 ? 1 : 0) +
    (state.damageTypes.length > 0 ? 1 : 0)
  );
}

/** Combines modal state and search text into an API request, or null when nothing is filtered. */
function buildRequest(state: ItemFilterState, search: string): ItemFilterRequest | null {
  const name = search.trim();
  if (countActiveFilters(state) === 0 && name === "") return null;

  return {
    itemTypes: state.itemTypes,
    rarities: state.rarities,
    magicOnly: state.magicOnly,
    attunementOnly: state.attunementOnly,
    weaponTypes: state.weaponTypes,
    damageTypes: state.damageTypes,
    page: 0,
    size: 5,
    name: name || null,
  };
}

/** Adds the value to the list, or removes it when already present. */
function toggle<T>(list: T[], value: T): T[] {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}

interface ChipGroupProps<T extends string> {
  label: string;
  options: T[];
  selected: T[];
  format?: (value: T) => string;
  onToggle: (value: T) => void;
}

/** A labelled group of toggleable chips, styled like the other compendium filters. */
function ChipGroup<T extends string>({ label, options, selected, format, onToggle }: ChipGroupProps<T>) {
  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-medium text-slate-700">{label}</span>
        {selected.length > 0 && <span className="text-xs text-slate-500">{selected.length} selected</span>}
      </div>
      <div className="flex flex-wrap gap-2 rounded-xl border border-slate-200 bg-slate-50 p-2">
        {options.map((option) => {
          const isSelected = selected.includes(option);
          return (
            <button
              key={option}
              type="button"
              aria-pressed={isSelected}
              onClick={() => onToggle(option)}
              className={[
                "rounded-full border px-3 py-1.5 text-[11px] font-medium uppercase tracking-wide transition",
                isSelected
                  ? "border-violet-500 bg-violet-100 text-violet-700"
                  : "border-slate-200 bg-white text-slate-600 hover:bg-slate-100",
              ].join(" ")}
            >
              {format ? format(option) : option}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/**
 * Search bar plus a filter sheet for items.
 * Typing searches automatically after a short pause (or immediately on Enter); the sheet holds
 * type, rarity, magic/attunement, and weapon/damage type filters, which apply when "Apply" is tapped.
 */
export function ItemFilter({ onApply, initialFilters = null }: ItemFilterProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState(initialFilters?.name ?? "");
  const [applied, setApplied] = useState<ItemFilterState>(() => stateFromRequest(initialFilters));
  const [draft, setDraft] = useState<ItemFilterState>(applied);
  const [referenceData, setReferenceData] = useState<ItemReferenceData | null>(null);
  const [referenceError, setReferenceError] = useState<string | null>(null);
  const appliedRef = useRef(applied);
  const lastEmittedRef = useRef(JSON.stringify(initialFilters));
  const debounceRef = useRef<number | undefined>(undefined);

  const activeCount = countActiveFilters(applied);

  /** Sends a request upstream, skipping it when it is identical to the last one sent. */
  const emit = (state: ItemFilterState, searchText: string) => {
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

  /** Loads the weapon/damage type options the first time the sheet opens. */
  useEffect(() => {
    if (!menuOpen || referenceData) return;
    let isMounted = true;
    getItemReferenceData()
      .then((data) => {
        if (isMounted) {
          setReferenceData(data);
          setReferenceError(null);
        }
      })
      .catch((err: unknown) => {
        if (isMounted) setReferenceError(err instanceof Error ? err.message : "Unable to load weapon filters");
      });
    return () => {
      isMounted = false;
    };
  }, [menuOpen, referenceData]);

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
            placeholder="Search items..."
            aria-label="Search items by name"
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
          aria-label={activeCount > 0 ? `Open item filters (${activeCount} active)` : "Open item filters"}
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
            aria-labelledby="item-filter-title"
            onSubmit={applyFilters}
            className="flex h-[85dvh] w-full sm:h-auto sm:max-h-[90dvh] max-w-xl flex-col rounded-t-2xl border border-slate-200 bg-white shadow-2xl sm:rounded-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex shrink-0 items-center justify-between gap-3 border-b border-slate-100 p-4">
              <h3 id="item-filter-title" className="text-lg font-semibold text-slate-900">Item Filters</h3>
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
                label="Type"
                options={ITEM_TYPES}
                selected={draft.itemTypes}
                format={formatItemType}
                onToggle={(value) => setDraft((current) => ({ ...current, itemTypes: toggle(current.itemTypes, value) }))}
              />

              <ChipGroup
                label="Rarity"
                options={ITEM_RARITIES}
                selected={draft.rarities}
                format={formatItemRarity}
                onToggle={(value) => setDraft((current) => ({ ...current, rarities: toggle(current.rarities, value) }))}
              />

              <div className="space-y-2">
                <label className="flex items-center gap-2 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    className="h-4 w-4"
                    checked={draft.magicOnly}
                    onChange={(event) => setDraft((current) => ({ ...current, magicOnly: event.target.checked }))}
                  />
                  Magic items only
                </label>
                <label className="flex items-center gap-2 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    className="h-4 w-4"
                    checked={draft.attunementOnly}
                    onChange={(event) => setDraft((current) => ({ ...current, attunementOnly: event.target.checked }))}
                  />
                  Requires attunement
                </label>
              </div>

              {referenceError && (
                <p className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
                  Unable to load weapon filters: {referenceError}
                </p>
              )}

              {referenceData && (
                <>
                  <ChipGroup
                    label="Weapon Type"
                    options={referenceData.weaponTypes.map((type) => type.name)}
                    selected={draft.weaponTypes}
                    onToggle={(value) => setDraft((current) => ({ ...current, weaponTypes: toggle(current.weaponTypes, value) }))}
                  />
                  <ChipGroup
                    label="Damage Type"
                    options={referenceData.damageTypes.map((type) => type.name)}
                    selected={draft.damageTypes}
                    onToggle={(value) => setDraft((current) => ({ ...current, damageTypes: toggle(current.damageTypes, value) }))}
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

export default ItemFilter;
