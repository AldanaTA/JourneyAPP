import { lazy, Suspense, useEffect, useState } from "react";
import { Search } from "lucide-react";
import { filterContent } from "@/apis/campaignApi";
import type { ContentKind } from "@/apis/campaignApi";
import { getContentSources } from "@/apis/characterApi";
import { searchRules } from "@/apis/rulesApi";
import type { Creature } from "@/models/Creature";
import type { Item } from "@/models/Item";
import type { Job } from "@/models/Job";
import type { Power } from "@/models/Power";
import type { Race } from "@/models/Race";
import type { RuleSearchResult } from "@/models/Rules";
import type { Trait } from "@/models/Trait";
import { cn } from "@/lib/utils";
import { inputClass } from "../../characters/editor/ui";
import { useCampaign } from "../campaignStore";
import { KINDS } from "../prep/contentKinds";
import type { ContentTabKind } from "../prep/contentKinds";

const TraitDetailsModal = lazy(() => import("../../compendium/components/traits/TraitDetailsModal").then((m) => ({ default: m.TraitDetailsModal })));
const PowerDetailsModal = lazy(() => import("../../compendium/components/powers/PowerDetailsModal").then((m) => ({ default: m.PowerDetailsModal })));
const ItemDetailsModal = lazy(() => import("../../compendium/components/items/ItemDetailsModal").then((m) => ({ default: m.ItemDetailsModal })));
const CreatureDetailsModal = lazy(() => import("../../compendium/components/creatures/CreatureDetailsModal").then((m) => ({ default: m.CreatureDetailsModal })));
const RaceDetailsModal = lazy(() => import("../../compendium/components/races/RaceDetailsModal").then((m) => ({ default: m.RaceDetailsModal })));
const JobDetailsModal = lazy(() => import("../../compendium/components/jobs/JobDetailsModal").then((m) => ({ default: m.JobDetailsModal })));
const RuleSectionDetailsModal = lazy(() => import("../../compendium/components/rules/RuleSectionDetailsModal").then((m) => ({ default: m.RuleSectionDetailsModal })));

type Scope = "all" | ContentTabKind | "rules";
type Hit =
  | { kind: ContentTabKind; id: string; name: string; summary: string; entry: unknown }
  | { kind: "rules"; id: string; name: string; summary: string; entry: RuleSearchResult };

const SCOPES: { value: Scope; label: string }[] = [
  { value: "all", label: "Everything" },
  ...(Object.keys(KINDS) as ContentTabKind[]).map((k) => ({ value: k, label: KINDS[k].label })),
  { value: "rules", label: "Rules" },
];

/**
 * Looks things up during play: the campaign's content, its allowed books, and the core rules, plus rules text.
 * Tap a result for the full entry.
 */
export default function SearchTool() {
  const campaign = useCampaign();
  const [sourceIds, setSourceIds] = useState<string[] | null>(null);
  useEffect(() => {
    getContentSources()
      .then((all) => setSourceIds([campaign.id, ...all.filter((s) => s.kind === "core").map((s) => s.id), ...campaign.allowedSources.map((s) => s.id)]))
      .catch(() => setSourceIds([campaign.id]));
  }, [campaign.id, campaign.allowedSources]);
  return <CompendiumSearch sourceIds={sourceIds} hint="Search this campaign's content, its books, and the core rules." />;
}

/**
 * Searches the compendium within some content sources (null while they load): traits, powers, items, creatures,
 * races, jobs, and rules text. Tap a result for its full card. Used in play and while building a character.
 */
export function CompendiumSearch({ sourceIds, hint }: { sourceIds: string[] | null; hint: string }) {
  const [query, setQuery] = useState("");
  const [scope, setScope] = useState<Scope>("all");
  const [hits, setHits] = useState<Hit[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState<Hit | null>(null);

  useEffect(() => {
    const q = query.trim();
    if (!q || !sourceIds) return;
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setLoading(true);
      const kinds = scope === "all" ? (Object.keys(KINDS) as ContentTabKind[]) : scope === "rules" ? [] : [scope];
      const size = scope === "all" ? 4 : 20;
      const contentSearches = kinds.map((kind) =>
        filterContent<{ id: string }>(kind as ContentKind, { ...KINDS[kind].body(q, "", ""), sourceIds, page: 0, size })
          .then((page) => page.content.map((entry): Hit => ({
            kind, id: entry.id, name: KINDS[kind].nameOf(entry as never), summary: KINDS[kind].summary(entry as never), entry,
          })))
          .catch(() => [] as Hit[]),
      );
      const ruleSearch = scope === "all" || scope === "rules"
        ? searchRules(q, undefined, scope === "rules" ? 20 : 5)
            .then((results) => results.map((r): Hit => ({ kind: "rules", id: r.sectionId, name: r.sectionTitle, summary: r.chapterTitle, entry: r })))
            .catch(() => [] as Hit[])
        : Promise.resolve([] as Hit[]);
      Promise.all([...contentSearches, ruleSearch]).then((lists) => {
        if (!cancelled) {
          setHits(lists.flat());
          setLoading(false);
        }
      });
    }, 300);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [query, scope, sourceIds]);

  const close = () => setOpen(null);
  return (
    <div className="space-y-2">
      <div className="flex gap-1.5">
        <div className="relative flex-1">
          <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search traits, creatures, rules…" aria-label="Search" className={cn(inputClass, "py-1.5 pl-8")} />
        </div>
        <select value={scope} onChange={(e) => setScope(e.target.value as Scope)} aria-label="Search in" className="rounded-lg border border-slate-200 bg-white px-2 text-sm">
          {SCOPES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>
      </div>
      {!query.trim() ? (
        <p className="py-6 text-center text-sm text-slate-400">{hint}</p>
      ) : (
        <ul className={cn("space-y-1 transition-opacity", loading && "opacity-50")}>
          {!loading && hits.length === 0 && <li className="py-4 text-center text-sm text-slate-400">Nothing found.</li>}
          {hits.map((h) => (
            <li key={`${h.kind}:${h.id}`}>
              <button type="button" onClick={() => setOpen(h)} className="flex w-full items-start gap-2 rounded-lg px-2 py-1.5 text-left hover:bg-slate-50">
                <span className="mt-0.5 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-slate-500">
                  {h.kind === "rules" ? "Rule" : KINDS[h.kind].singular}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-slate-900">{h.name}</span>
                  <span className="block truncate text-xs text-slate-500">{h.summary}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      <Suspense fallback={null}>
        {open?.kind === "traits" && <TraitDetailsModal trait={open.entry as Trait} isOpen onClose={close} />}
        {open?.kind === "powers" && <PowerDetailsModal power={open.entry as Power} isOpen onClose={close} />}
        {open?.kind === "items" && <ItemDetailsModal item={open.entry as Item} isOpen onClose={close} />}
        {open?.kind === "creatures" && <CreatureDetailsModal creature={open.entry as Creature} isOpen onClose={close} />}
        {open?.kind === "races" && <RaceDetailsModal race={open.entry as Race} isOpen onClose={close} />}
        {open?.kind === "jobs" && <JobDetailsModal job={open.entry as Job} isOpen onClose={close} />}
        {open?.kind === "rules" && <RuleSectionDetailsModal result={open.entry} isOpen onClose={close} />}
      </Suspense>
    </div>
  );
}
