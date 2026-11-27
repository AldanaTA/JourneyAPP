import { lazy, Suspense, useState } from "react";
import { filterRaces } from "@/apis/raceApi";
import type { Race } from "@/models/Race";
import { useSearchSourceIds } from "../referenceStore";
import { SIZE_NAMES } from "../rules";
import { PickerModal } from "./PickerModal";
import type { RaceFilterRequest } from "@/apis/raceApi";
import { RaceFilter } from "@/sections/compendium/components/filters/RaceFilter";

const NO_RACE_FILTERS: RaceFilterRequest = { sizes: [], movement: null, movementOperator: "lte", page: 0, size: 20, name: null, traitName: null };

const RaceDetailsModal = lazy(() =>
  import("@/sections/compendium/components/races/RaceDetailsModal").then((m) => ({ default: m.RaceDetailsModal })),
);

function sizeRange(race: Race): string {
  return race.maxRaceSize && race.maxRaceSize !== race.minRaceSize
    ? `${SIZE_NAMES[race.minRaceSize]}–${SIZE_NAMES[race.maxRaceSize]}`
    : SIZE_NAMES[race.minRaceSize];
}

/** Pick one race, or several for a mixed race. */
export default function RacePicker({
  current,
  onPick,
  onClose,
}: {
  /** The races already chosen (as minimal Race objects). */
  current: Race[];
  onPick: (races: Race[]) => void;
  onClose: () => void;
}) {
  const sourceIds = useSearchSourceIds();
  const [request, setRequest] = useState<RaceFilterRequest | null>(null);
  return (
    <PickerModal<Race>
      title="Choose race"
      subtitle="Pick more than one for a mixed race; it then chooses 50 EXP of racial traits."
      searchPlaceholder="Search races"
      hideSearch
      filters={<div className="w-full"><RaceFilter onApply={setRequest} initialFilters={request} /></div>}
      filterKey={`${JSON.stringify(request)}|${sourceIds.join(",")}`}
      load={(page, pageSize) => filterRaces({ ...(request ?? NO_RACE_FILTERS), sourceIds, page, size: pageSize }, page, pageSize)}
      getId={(r) => r.id}
      renderFullDetails={(r, close) => (
        <Suspense fallback={null}>
          <RaceDetailsModal race={r} isOpen onClose={close} />
        </Suspense>
      )}
      multiple
      initialSelected={current}
      renderRow={(r) => (
        <>
          <span className="block font-semibold text-slate-900">{r.raceName}</span>
          <span className="block text-xs text-slate-500">
            {sizeRange(r)} · {r.movement} ft · {r.languages}
          </span>
        </>
      )}
      renderDetails={(r) => (
        <span className="block space-y-1 text-sm text-slate-600">
          {r.raceDescription && <span className="block line-clamp-4">{r.raceDescription}</span>}
          {r.traits.length > 0 && (
            <span className="block text-xs">
              <span className="font-semibold">Racial traits:</span>{" "}
              {r.traits.map((t) => `${t.traitName} (${t.purchaseCost ?? 0} EXP)`).join(", ")}
            </span>
          )}
        </span>
      )}
      confirmLabel={(selected) => (selected.length > 1 ? `Choose ${selected.length} races` : "Choose race")}
      footerNote={(selected) => (selected.length > 1 ? "Mixed race" : null)}
      onConfirm={(selected) => {
        onPick(selected);
        onClose();
      }}
      onClose={onClose}
    />
  );
}
