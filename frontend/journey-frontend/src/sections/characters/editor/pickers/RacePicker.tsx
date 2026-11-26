import { useState } from "react";
import { filterRaces } from "@/apis/raceApi";
import type { Race, RaceSize } from "@/models/Race";
import { useSearchSourceIds } from "../referenceStore";
import { SIZE_NAMES, SIZE_ORDER } from "../rules";
import { FilterChips, PickerModal } from "./PickerModal";

const SIZES = SIZE_ORDER.map((s) => ({ value: s, label: SIZE_NAMES[s] }));

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
  const [size, setSize] = useState<RaceSize | null>(null);
  return (
    <PickerModal<Race>
      title="Choose race"
      subtitle="Pick more than one for a mixed race; it then chooses 50 EXP of racial traits."
      searchPlaceholder="Search races"
      filters={<FilterChips label="Size" options={SIZES} value={size} onChange={setSize} />}
      filterKey={`${size}|${sourceIds.join(",")}`}
      load={(page, pageSize, search) =>
        filterRaces(
          { sizes: size ? [size] : [], movement: null, movementOperator: "lte", page, size: pageSize, name: search || null, traitName: null, sourceIds },
          page,
          pageSize,
        )
      }
      getId={(r) => r.id}
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
