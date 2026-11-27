import { lazy, Suspense, useState } from "react";
import { filterTraits } from "@/apis/traitApi";
import type { Trait } from "@/models/Trait";
import { formatTraitActivationCost, formatTraitUses } from "@/sections/compendium/components/traits/traitFormatters";
import { useSearchSourceIds } from "../referenceStore";
import { FilterChips, PickerModal } from "./PickerModal";
import type { TraitFilterRequest } from "@/apis/traitApi";
import { TraitFilter } from "@/sections/compendium/components/filters/TraitFilter";

/** No filters: every trait (the compendium filter sends null when nothing is set). */
const NO_TRAIT_FILTERS: TraitFilterRequest = {
  categories: [], matchAll: false, page: 0, size: 20, lvl: null, lvlOperator: "lte", purchaseCost: null, purchaseOperator: "lte",
  tpCost: null, tpOperator: "lte", mpCost: null, mpOperator: "lte", epCost: null, epOperator: "lte", name: null,
};

const TraitDetailsModal = lazy(() =>
  import("@/sections/compendium/components/traits/TraitDetailsModal").then((m) => ({ default: m.TraitDetailsModal })),
);

/** Buy one trait with EXP. "Affordable" hides traits costing more than the EXP left. */
export default function TraitPicker({
  expRemaining,
  ownedIds,
  onPick,
  onClose,
}: {
  expRemaining: number;
  ownedIds: Set<string>;
  onPick: (trait: Trait) => void;
  onClose: () => void;
}) {
  const sourceIds = useSearchSourceIds();
  const [request, setRequest] = useState<TraitFilterRequest | null>(null);
  const [affordable, setAffordable] = useState<"yes" | null>(null);
  return (
    <PickerModal<Trait>
      title="Buy a trait"
      subtitle={`${expRemaining} EXP left`}
      searchPlaceholder="Search traits"
      hideSearch
      filters={
        <div className="flex w-full flex-wrap items-center gap-2">
          <div className="min-w-0 flex-1"><TraitFilter onApply={setRequest} initialFilters={request} /></div>
          <FilterChips label="Cost" options={[{ value: "yes", label: "Affordable" }]} value={affordable} onChange={setAffordable} />
        </div>
      }
      filterKey={`${JSON.stringify(request)}|${affordable}|${sourceIds.join(",")}`}
      load={(page, size) => {
        const base = request ?? NO_TRAIT_FILTERS;
        const cap = affordable && base.purchaseCost === null;
        return filterTraits(
          { ...base, purchaseCost: cap ? Math.max(0, expRemaining) : base.purchaseCost, purchaseOperator: cap ? "lte" : base.purchaseOperator, sourceIds, page, size },
          page,
          size,
        );
      }}
      getId={(t) => t.id}
      renderFullDetails={(t, close) => (
        <Suspense fallback={null}>
          <TraitDetailsModal trait={t} isOpen onClose={close} />
        </Suspense>
      )}
      disabledReason={(t) =>
        ownedIds.has(t.id)
          ? "Already has this trait"
          : (t.purchaseCost ?? 0) > expRemaining
            ? `Needs ${t.purchaseCost} EXP`
            : null
      }
      renderRow={(t) => {
        const cost = formatTraitActivationCost(t);
        return (
          <>
            <span className="flex items-baseline justify-between gap-2">
              <span className="font-semibold text-slate-900">{t.traitName}</span>
              <span className="shrink-0 text-xs font-semibold text-violet-700">{t.purchaseCost ?? 0} EXP</span>
            </span>
            <span className="block text-xs text-slate-500">{cost.length ? cost.join(" · ") : formatTraitUses(t)}</span>
          </>
        );
      }}
      renderDetails={(t) => (
        <span className="block space-y-1 text-sm text-slate-600">
          {t.traitDesc && <span className="block">{t.traitDesc}</span>}
          {t.traitEffect && <span className="block whitespace-pre-line">{t.traitEffect}</span>}
        </span>
      )}
      confirmLabel={([t]) => (t ? `Buy for ${t.purchaseCost ?? 0} EXP` : "Buy trait")}
      onConfirm={([trait]) => {
        if (trait) onPick(trait);
        onClose();
      }}
      onClose={onClose}
    />
  );
}
