import { useEffect, useEffectEvent, useState } from "react";
import type { ReactNode } from "react";
import { Check, Search } from "lucide-react";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import { EmptyNote, Modal, PrimaryButton, SecondaryButton, inputClass } from "../ui";

export interface PickerPage<T> {
  content: T[];
  totalPages: number;
}

const PAGE_SIZE = 20;

/**
 * Search, filter, and pick from a paged compendium list. Results load 20 at a time, only while the dialog is
 * open. {@code filterKey} names the current filters: when it changes, the list starts over.
 */
export function PickerModal<T>({
  title,
  subtitle,
  searchPlaceholder,
  filters,
  filterKey,
  load,
  getId,
  renderRow,
  renderDetails,
  disabledReason,
  multiple = false,
  initialSelected = [],
  confirmLabel,
  footerNote,
  onConfirm,
  onClose,
}: {
  title: string;
  subtitle?: ReactNode;
  searchPlaceholder: string;
  /** Filter controls shown under the search bar. */
  filters?: ReactNode;
  filterKey: string;
  load: (page: number, size: number, search: string) => Promise<PickerPage<T>>;
  getId: (item: T) => string;
  renderRow: (item: T) => ReactNode;
  /** Shown under a row when it is selected. */
  renderDetails?: (item: T) => ReactNode;
  /** Why an item cannot be picked (shown instead of selecting it), or null. */
  disabledReason?: (item: T, selected: T[]) => string | null;
  multiple?: boolean;
  initialSelected?: T[];
  confirmLabel: (selected: T[]) => string;
  footerNote?: (selected: T[]) => ReactNode;
  onConfirm: (selected: T[]) => void;
  onClose: () => void;
}) {
  const [search, setSearch] = useState("");
  const [debounced, setDebounced] = useState("");
  const [items, setItems] = useState<T[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<T[]>(initialSelected);
  const fetchPage = useEffectEvent((pageNumber: number, text: string) => load(pageNumber, PAGE_SIZE, text));

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(search.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [search]);

  // A new search or filter starts the list over.
  const query = `${debounced}\u0000${filterKey}`;
  const [loadedQuery, setLoadedQuery] = useState<string | null>(null);
  if (loadedQuery !== query) {
    // Keep showing the old results (dimmed) until the new ones arrive, so the list does not jump.
    setLoadedQuery(query);
    setPage(0);
    setLoading(true);
  }

  useEffect(() => {
    let cancelled = false;
    fetchPage(page, debounced)
      .then((result) => {
        if (cancelled) return;
        setItems((previous) => (page === 0 ? result.content : [...previous, ...result.content]));
        setTotalPages(result.totalPages);
        setError(null);
      })
      .catch((reason: unknown) => {
        if (!cancelled) setError(reason instanceof Error ? reason.message : "Could not load the list");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [query, page, debounced]);

  const selectedIds = new Set(selected.map(getId));
  const toggle = (item: T) => {
    const id = getId(item);
    if (selectedIds.has(id)) {
      setSelected(selected.filter((s) => getId(s) !== id));
    } else {
      setSelected(multiple ? [...selected, item] : [item]);
    }
  };

  return (
    <Modal
      title={title}
      subtitle={subtitle}
      onClose={onClose}
      wide
      tall
      footer={
        <div className="flex w-full flex-wrap items-center justify-between gap-2">
          <div className="text-sm text-slate-500">{footerNote?.(selected)}</div>
          <div className="flex gap-2">
            <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
            <PrimaryButton disabled={selected.length === 0 && !multiple} onClick={() => onConfirm(selected)}>
              {confirmLabel(selected)}
            </PrimaryButton>
          </div>
        </div>
      }
    >
      <div className="space-y-3">
        <div className="relative">
          <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          {loading && page === 0 && (
            <Spinner className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          )}
          <input
            autoFocus
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder={searchPlaceholder}
            aria-label={searchPlaceholder}
            className={cn(inputClass, "pl-9")}
          />
        </div>
        {filters && <div className="flex flex-wrap items-center gap-2">{filters}</div>}

        {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        <ul className={cn("space-y-1.5 transition-opacity", loading && page === 0 && "opacity-50")}>
          {items.map((item) => {
            const id = getId(item);
            const isSelected = selectedIds.has(id);
            const reason = isSelected ? null : (disabledReason?.(item, selected) ?? null);
            return (
              <li key={id}>
                <button
                  type="button"
                  onClick={() => toggle(item)}
                  disabled={reason !== null}
                  aria-pressed={isSelected}
                  className={cn(
                    "flex w-full items-start gap-3 rounded-xl border px-3 py-2.5 text-left transition",
                    isSelected ? "border-violet-400 bg-violet-50" : "border-slate-200 bg-white hover:border-slate-300",
                    reason !== null && "cursor-not-allowed opacity-50",
                  )}
                >
                  <span
                    className={cn(
                      "mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center border",
                      multiple ? "rounded-md" : "rounded-full",
                      isSelected ? "border-violet-600 bg-violet-600 text-white" : "border-slate-300 bg-white",
                    )}
                  >
                    {isSelected && <Check size={13} strokeWidth={3} />}
                  </span>
                  <span className="min-w-0 flex-1">
                    {renderRow(item)}
                    {reason && <span className="mt-0.5 block text-xs text-amber-700">{reason}</span>}
                    {isSelected && renderDetails && <span className="mt-2 block">{renderDetails(item)}</span>}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>

        {loading && (page > 0 || items.length === 0) && (
          <div className="flex items-center justify-center gap-2 py-4 text-sm text-slate-500">
            <Spinner className="h-4 w-4" /> Loading…
          </div>
        )}
        {!loading && items.length === 0 && !error && <EmptyNote>Nothing matches.</EmptyNote>}
        {!loading && page + 1 < totalPages && (
          <div className="flex justify-center">
            <SecondaryButton onClick={() => { setLoading(true); setPage(page + 1); }}>Load more</SecondaryButton>
          </div>
        )}
      </div>
    </Modal>
  );
}

/** A row of toggle chips for a picker's filters. */
export function FilterChips<V extends string>({
  options,
  value,
  onChange,
  label,
}: {
  options: { value: V; label: string }[];
  value: V | null;
  onChange: (value: V | null) => void;
  label: string;
}) {
  return (
    <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={value === option.value}
          onClick={() => onChange(value === option.value ? null : option.value)}
          className={cn(
            "rounded-full border px-2.5 py-1 text-xs font-medium transition",
            value === option.value
              ? "border-violet-500 bg-violet-600 text-white"
              : "border-slate-200 bg-white text-slate-600 hover:border-slate-300",
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

/** A compact select for a picker's filters. */
export function FilterSelect<V extends string>({
  options,
  value,
  onChange,
  label,
}: {
  options: { value: V; label: string }[];
  value: V | "";
  onChange: (value: V | "") => void;
  label: string;
}) {
  return (
    <select
      aria-label={label}
      value={value}
      onChange={(event) => onChange(event.target.value as V | "")}
      className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-sm text-slate-700 outline-none focus:border-violet-400"
    >
      <option value="">{label}</option>
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  );
}
