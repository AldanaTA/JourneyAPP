import { useState } from "react";
import type { PowerFilterRequest } from "@/apis/powerApi";
import type { TraitFilterRequest } from "@/apis/traitApi";
import type { JobFilterRequest } from "@/apis/jobApi";
import type { RaceFilterRequest } from "@/apis/raceApi";
import { CategorySelector, type CompendiumCategory } from "./components/CategorySelector";
import { PowerFilter } from "./components/filters/PowerFilter";
import { TraitFilter } from "./components/filters/TraitFilter";
import { JobFilter } from "./components/filters/JobFilter";
import { RaceFilter } from "./components/filters/RaceFilter";
import { ItemFilter } from "./components/filters/ItemFilter";
import { CreatureFilter } from "./components/filters/CreatureFilter";
import { PowerLoader } from "./components/loaders/PowerLoader";
import { TraitLoader } from "./components/loaders/TraitLoader";
import { JobLoader } from "./components/loaders/JobLoader";
import { RaceLoader } from "./components/loaders/RaceLoader";
import { ItemLoader } from "./components/loaders/ItemLoader";
import { CreatureLoader } from "./components/loaders/CreatureLoader";

function CompendiumSection() {
  const [selectedCategory, setSelectedCategory] = useState<CompendiumCategory>("Powers");
  const [powerFilters, setPowerFilters] = useState<PowerFilterRequest | null>(null);
  const [traitFilters, setTraitFilters] = useState<TraitFilterRequest | null>(null);
  const [jobFilters, setJobFilters] = useState<JobFilterRequest | null>(null);
  const [raceFilters, setRaceFilters] = useState<RaceFilterRequest | null>(null);

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
        return <ItemFilter />;
      case "Creatures":
        return <CreatureFilter />;
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
        return <ItemLoader />;
      case "Creatures":
        return <CreatureLoader />;
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
            <div className="mb-3">
              <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-slate-500">
                Filters
              </p>
            </div>

            <div className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-medium text-slate-700">Current Category</span>
                <span className="rounded-full bg-violet-100 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-violet-700">
                  {selectedCategory}
                </span>
              </div>

              <div className="mt-3">{renderFilter()}</div>
            </div>
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

            <div>{renderLoader()}</div>
          </main>
        </div>
      </div>
    </div>
  );
}

export default CompendiumSection;
