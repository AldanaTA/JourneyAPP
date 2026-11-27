import { lazy, Suspense, useEffect, useState } from "react";
import { Search } from "lucide-react";
import { filterContent } from "@/apis/campaignApi";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import { EmptyNote, Fab, SecondaryButton, inputClass } from "../../characters/editor/ui";
import { FilterSelect } from "../../characters/editor/pickers/PickerModal";
import { useCampaign } from "../campaignStore";
import type { ContentTabKind } from "./contentKinds";
import { KINDS } from "./contentKinds";

const FORMS = {
  traits: lazy(() => import("../forms/TraitForm")),
  powers: lazy(() => import("../forms/PowerForm")),
  items: lazy(() => import("../forms/ItemForm")),
  creatures: lazy(() => import("../forms/CreatureForm")),
  races: lazy(() => import("../forms/RaceForm")),
  jobs: lazy(() => import("../forms/JobForm")),
};

const PAGE = 20;

interface Entry {
  id: string;
}

/** One kind of the campaign's own content: search and filter at the top, the list, and + to make more. */
export default function ContentTab({ kind }: { kind: ContentTabKind }) {
  const campaign = useCampaign();
  const config = KINDS[kind];
  const Form = FORMS[kind];
  const [search, setSearch] = useState("");
  const [debounced, setDebounced] = useState("");
  const [filter, setFilter] = useState("");
  const [items, setItems] = useState<Entry[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<Entry | "new" | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(search.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [search]);

  const query = `${debounced}|${filter}|${reloadKey}`;
  const [loadedQuery, setLoadedQuery] = useState<string | null>(null);
  if (loadedQuery !== query) {
    setLoadedQuery(query);
    setPage(0);
    setLoading(true);
  }

  useEffect(() => {
    let cancelled = false;
    filterContent<Entry>(kind, { ...config.body(debounced, filter, campaign.id), page, size: PAGE })
      .then((result) => {
        if (cancelled) return;
        setItems((previous) => (page === 0 ? result.content : [...previous, ...result.content]));
        setTotalPages(result.totalPages);
        setError(null);
      })
      .catch((reason: unknown) => !cancelled && setError(reason instanceof Error ? reason.message : "Could not load"))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [query, page, kind, config, campaign.id, debounced, filter]);

  const done = () => {
    setEditing(null);
    setReloadKey((k) => k + 1);
  };

  return (
    <div className="space-y-3">
      <div className="flex items-end justify-between">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">Campaign content</p>
          <h2 className="text-2xl font-semibold tracking-tight text-slate-900">{config.label}</h2>
        </div>
      </div>
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder={`Search ${config.label.toLowerCase()}`}
            aria-label={`Search ${config.label.toLowerCase()}`}
            className={cn(inputClass, "py-1.5 pl-8")}
          />
          {loading && page === 0 && <Spinner className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />}
        </div>
        <FilterSelect label={config.filterLabel} options={config.filterOptions} value={filter} onChange={(v) => setFilter(v)} />
      </div>

      {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      {!loading && items.length === 0 && !error && (
        <EmptyNote>
          {debounced || filter ? "Nothing matches." : `This campaign has no ${config.label.toLowerCase()} of its own yet. Tap + to make one.`}
        </EmptyNote>
      )}
      <ul className={cn("space-y-1.5 transition-opacity", loading && page === 0 && "opacity-50")}>
        {items.map((item) => (
          <li key={item.id}>
            <button
              type="button"
              onClick={() => setEditing(item)}
              className="block w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-left shadow-sm hover:border-violet-300"
            >
              <span className="block font-semibold text-slate-900">{config.nameOf(item as never)}</span>
              <span className="block text-xs text-slate-500">{config.summary(item as never)}</span>
            </button>
          </li>
        ))}
      </ul>
      {!loading && page + 1 < totalPages && (
        <div className="flex justify-center">
          <SecondaryButton
            onClick={() => {
              setLoading(true);
              setPage(page + 1);
            }}
          >
            Load more
          </SecondaryButton>
        </div>
      )}

      <Fab label={`New ${config.singular}`} onClick={() => setEditing("new")} className="bottom-24" />
      <Suspense fallback={null}>
        {editing && (
          <Form
            campaignId={campaign.id}
            initial={editing === "new" ? null : (editing as never)}
            onDone={done}
            onClose={() => setEditing(null)}
          />
        )}
      </Suspense>
    </div>
  );
}
