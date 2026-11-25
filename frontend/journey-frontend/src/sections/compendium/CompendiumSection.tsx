import { lazy, Suspense, useState } from "react";
import type { PowerFilterRequest } from "@/apis/powerApi";
import type { TraitFilterRequest } from "@/apis/traitApi";
import type { JobFilterRequest } from "@/apis/jobApi";
import type { RaceFilterRequest } from "@/apis/raceApi";
import type { ItemFilterRequest } from "@/apis/itemApi";
import type { CreatureFilterRequest } from "@/apis/creatureApi";
import { CategorySelector, type CompendiumCategory } from "./components/CategorySelector";
import { Spinner } from "@/components/ui/spinner";
import type { RuleFilterRequest } from "@/apis/rulesApi";

// Each tab's filter and list are downloaded only when that tab is first opened.
const PowerFilter = lazy(() => import("./components/filters/PowerFilter"));
const TraitFilter = lazy(() => import("./components/filters/TraitFilter"));
const JobFilter = lazy(() => import("./components/filters/JobFilter"));
const RaceFilter = lazy(() => import("./components/filters/RaceFilter"));
const ItemFilter = lazy(() => import("./components/filters/ItemFilter"));
const CreatureFilter = lazy(() => import("./components/filters/CreatureFilter"));
const RuleFilter = lazy(() => import("./components/filters/RuleFilter"));
const PowerLoader = lazy(() => import("./components/loaders/PowerLoader"));
const TraitLoader = lazy(() => import("./components/loaders/TraitLoader"));
const JobLoader = lazy(() => import("./components/loaders/JobLoader"));
const RaceLoader = lazy(() => import("./components/loaders/RaceLoader"));
const ItemLoader = lazy(() => import("./components/loaders/ItemLoader"));
const CreatureLoader = lazy(() => import("./components/loaders/CreatureLoader"));
const RuleLoader = lazy(() => import("./components/loaders/RuleLoader"));

/** Placeholder the size of a filter's search row while its code downloads. */
function FilterLoading() {
  return <div className="h-10 animate-pulse rounded-lg border border-slate-200 bg-white" aria-hidden="true" />;
}

/** Shown in the content panel while a tab's list code downloads. */
function TabLoading() {
  return (
    <div className="flex min-h-[180px] items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-slate-600">
      <div className="flex items-center gap-2">
        <Spinner className="h-5 w-5" />
        <span>Loading...</span>
      </div>
    </div>
  );
}

function CompendiumSection() {
  const [selectedCategory, setSelectedCategory] = useState<CompendiumCategory>("Rules");
  const [powerFilters, setPowerFilters] = useState<PowerFilterRequest | null>(null);
  const [traitFilters, setTraitFilters] = useState<TraitFilterRequest | null>(null);
  const [jobFilters, setJobFilters] = useState<JobFilterRequest | null>(null);
  const [raceFilters, setRaceFilters] = useState<RaceFilterRequest | null>(null);
  const [itemFilters, setItemFilters] = useState<ItemFilterRequest | null>(null);
  const [creatureFilters, setCreatureFilters] = useState<CreatureFilterRequest | null>(null);
  const [ruleFilters, setRuleFilters] = useState<RuleFilterRequest | null>(null);

  const renderFilter = () => {
    switch (selectedCategory) {
      case "Powers":
        return <PowerFilter onApply={setPowerFilters} initialFilters={powerFilters} />;
      case "Traits":
        return <TraitFilter onApply={setTraitFilters} initialFilters={traitFilters} />;
      case "Jobs":
        return <JobFilter onApply={setJobFilters} initialFilters={jobFilters} />;
      case "Races":
        return <RaceFilter onApply={setRaceFilters} initialFilters={raceFilters} />;
      case "Items":
        return <ItemFilter onApply={setItemFilters} initialFilters={itemFilters} />;
      case "Creatures":
        return <CreatureFilter onApply={setCreatureFilters} initialFilters={creatureFilters} />;
      case "Rules":
        return <RuleFilter onApply={setRuleFilters} initialFilters={ruleFilters} />;
      default:
        return null;
    }
  };

  const renderLoader = () => {
    switch (selectedCategory) {
      case "Powers":
        return <PowerLoader filters={powerFilters} />;
      case "Traits":
        return <TraitLoader filters={traitFilters} />;
      case "Jobs":
        return <JobLoader filters={jobFilters} />;
      case "Races":
        return <RaceLoader filters={raceFilters} />;
      case "Items":
        return <ItemLoader filters={itemFilters} />;
      case "Creatures":
        return <CreatureLoader filters={creatureFilters} />;
      case "Rules":
        return <RuleLoader filters={ruleFilters} />;
      default:
        return null;
    }
  };

  return (
    <div className="space-y-5">
      <header className="flex flex-col gap-2 border-b border-slate-200 pb-3">
        <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">
          Reference
        </p>
        <h2 className="text-2xl font-semibold tracking-tight text-slate-900 md:text-3xl">
          Compendium
        </h2>
      </header>

      <div className="space-y-4">
        <CategorySelector value={selectedCategory} onChange={setSelectedCategory} />

        <div className="grid gap-4 md:grid-cols-[280px_minmax(0,1fr)]">
          <aside className="rounded-2xl border border-slate-200 bg-slate-50 p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between gap-2">
              <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-slate-500">
                Filters
              </p>
              <span className="rounded-full bg-violet-100 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-violet-700">
                {selectedCategory}
              </span>
            </div>

            <Suspense fallback={<FilterLoading />}>{renderFilter()}</Suspense>
          </aside>

          <main className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-slate-500">
                  Content
                </p>
                <h3 className="mt-1 text-lg font-semibold text-slate-900">{selectedCategory}</h3>
              </div>
            </div>

            <div><Suspense fallback={<TabLoading />}>{renderLoader()}</Suspense></div>
          </main>
        </div>
      </div>
    </div>
  );
}

export default CompendiumSection;
