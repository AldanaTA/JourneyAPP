import { useEffect, useState } from "react";
import { Search, X } from "lucide-react";
import { getContentSources } from "@/apis/characterApi";
import { filterTraits } from "@/apis/traitApi";
import type { Trait } from "@/models/Trait";
import { cn } from "@/lib/utils";
import { inputClass } from "../../characters/editor/ui";

/**
 * Chooses traits for a campaign race or job: the chosen ones as chips, and a search over official traits and the
 * campaign's own (the API refuses traits from other campaigns).
 */
export function TraitChooser({ campaignId, chosen, onChange, label }: {
  campaignId: string;
  chosen: { id: string; name: string }[];
  onChange: (traits: { id: string; name: string }[]) => void;
  label: string;
}) {
  const [sourceIds, setSourceIds] = useState<string[] | null>(null);
  const [search, setSearch] = useState("");
  const [results, setResults] = useState<Trait[]>([]);

  useEffect(() => {
    getContentSources()
      .then((sources) => setSourceIds([campaignId, ...sources.filter((s) => s.kind !== "campaign").map((s) => s.id)]))
      .catch(() => setSourceIds([campaignId]));
  }, [campaignId]);

  useEffect(() => {
    if (!sourceIds || !search.trim()) return;
    let cancelled = false;
    const timer = window.setTimeout(() => {
      filterTraits(
        {
          categories: [], matchAll: false, page: 0, size: 8, lvl: null, lvlOperator: "lte", purchaseCost: null, purchaseOperator: "lte",
          tpCost: null, tpOperator: "lte", mpCost: null, mpOperator: "lte", epCost: null, epOperator: "lte", name: search.trim(), sourceIds,
        },
        0,
        8,
      )
        .then((page) => !cancelled && setResults(page.content))
        .catch(() => undefined);
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [search, sourceIds]);

  const chosenIds = new Set(chosen.map((t) => t.id));
  return (
    <div className="space-y-2">
      <span className="block text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</span>
      {chosen.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {chosen.map((t) => (
            <span key={t.id} className="flex items-center gap-1 rounded-full bg-violet-100 py-1 pl-2.5 pr-1 text-xs font-medium text-violet-800">
              {t.name}
              <button type="button" aria-label={`Remove ${t.name}`} onClick={() => onChange(chosen.filter((c) => c.id !== t.id))} className="rounded-full p-0.5 hover:bg-violet-200">
                <X size={12} />
              </button>
            </span>
          ))}
        </div>
      )}
      <div className="relative">
        <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search traits to add"
          aria-label="Search traits to add"
          className={cn(inputClass, "py-1.5 pl-8")}
        />
      </div>
      {search.trim() && (
        <ul className="max-h-48 overflow-y-auto rounded-xl border border-slate-200">
          {results.length === 0 && <li className="px-3 py-2 text-sm text-slate-500">No traits found.</li>}
          {results.map((t) => (
            <li key={t.id}>
              <button
                type="button"
                disabled={chosenIds.has(t.id)}
                onClick={() => onChange([...chosen, { id: t.id, name: t.traitName }])}
                className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-slate-50 disabled:opacity-40"
              >
                <span className="font-medium text-slate-900">{t.traitName}</span>
                <span className="text-xs text-slate-500">{t.contentSourceName} · {t.purchaseCost ?? 0} EXP</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
