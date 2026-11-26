import { useState } from "react";
import { filterPowers } from "@/apis/powerApi";
import type { Power, PowerType } from "@/models/Power";
import { formatPowerCost } from "@/sections/compendium/components/powers/powerFormatters";
import { useSearchSourceIds } from "../referenceStore";
import { POWER_COST } from "../rules";
import { FilterChips, PickerModal } from "./PickerModal";

const POWER_TYPES: PowerType[] = ["Destruction", "Support", "Sabotage", "Utility"];

/** Learn one power (10 EXP). */
export default function PowerPicker({
  expRemaining,
  ownedIds,
  onPick,
  onClose,
}: {
  expRemaining: number;
  ownedIds: Set<string>;
  onPick: (power: Power) => void;
  onClose: () => void;
}) {
  const sourceIds = useSearchSourceIds();
  const [type, setType] = useState<PowerType | null>(null);
  return (
    <PickerModal<Power>
      title="Learn a power"
      subtitle={`${POWER_COST} EXP each · ${expRemaining} EXP left`}
      searchPlaceholder="Search powers"
      filters={<FilterChips label="Type" options={POWER_TYPES.map((t) => ({ value: t, label: t }))} value={type} onChange={setType} />}
      filterKey={`${type}|${sourceIds.join(",")}`}
      load={(page, size, search) =>
        filterPowers(
          {
            type,
            categories: [],
            matchAll: false,
            page,
            size,
            tpCost: null,
            tpOperator: "lte",
            hpCost: null,
            hpOperator: "lte",
            mpCost: null,
            mpOperator: "lte",
            epCost: null,
            epOperator: "lte",
            name: search || null,
            sourceIds,
          },
          page,
          size,
        )
      }
      getId={(p) => p.id}
      disabledReason={(p) => (ownedIds.has(p.id) ? "Already known" : expRemaining < POWER_COST ? `Needs ${POWER_COST} EXP` : null)}
      renderRow={(p) => (
        <>
          <span className="flex items-baseline justify-between gap-2">
            <span className="font-semibold text-slate-900">{p.name}</span>
            <span className="shrink-0 text-xs text-slate-500">{p.type}</span>
          </span>
          <span className="block text-xs text-slate-500">{formatPowerCost(p).join(" · ") || "No cost"}</span>
        </>
      )}
      renderDetails={(p) => (
        <span className="block space-y-1 text-sm text-slate-600">
          {p.range && <span className="block text-xs">Range {p.range}{p.area ? ` · Area ${p.area}` : ""}{p.duration ? ` · ${p.duration}` : ""}</span>}
          {p.effect && <span className="block whitespace-pre-line">{p.effect}</span>}
        </span>
      )}
      confirmLabel={() => `Learn for ${POWER_COST} EXP`}
      onConfirm={([power]) => {
        if (power) onPick(power);
        onClose();
      }}
      onClose={onClose}
    />
  );
}
