import { lazy, Suspense, useState } from "react";
import { filterPowers } from "@/apis/powerApi";
import type { Power } from "@/models/Power";
import { formatPowerCost } from "@/sections/compendium/components/powers/powerFormatters";
import { useSearchSourceIds } from "../referenceStore";
import { POWER_COST } from "../rules";
import { PickerModal } from "./PickerModal";
import type { PowerFilterRequest } from "@/apis/powerApi";
import { PowerFilter } from "@/sections/compendium/components/filters/PowerFilter";

const NO_POWER_FILTERS: PowerFilterRequest = {
  type: null, categories: [], matchAll: false, page: 0, size: 20, tpCost: null, tpOperator: "lte", hpCost: null, hpOperator: "lte",
  mpCost: null, mpOperator: "lte", epCost: null, epOperator: "lte", name: null,
};

const PowerDetailsModal = lazy(() =>
  import("@/sections/compendium/components/powers/PowerDetailsModal").then((m) => ({ default: m.PowerDetailsModal })),
);


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
  const [request, setRequest] = useState<PowerFilterRequest | null>(null);
  return (
    <PickerModal<Power>
      title="Learn a power"
      subtitle={`${POWER_COST} EXP each · ${expRemaining} EXP left`}
      searchPlaceholder="Search powers"
      hideSearch
      filters={<div className="w-full"><PowerFilter onApply={setRequest} initialFilters={request} /></div>}
      filterKey={`${JSON.stringify(request)}|${sourceIds.join(",")}`}
      load={(page, size) => filterPowers({ ...(request ?? NO_POWER_FILTERS), sourceIds, page, size }, page, size)}
      getId={(p) => p.id}
      renderFullDetails={(p, close) => (
        <Suspense fallback={null}>
          <PowerDetailsModal power={p} isOpen onClose={close} />
        </Suspense>
      )}
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
