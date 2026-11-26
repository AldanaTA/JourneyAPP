import { useState } from "react";
import { filterTraits } from "@/apis/traitApi";
import type { Trait, TraitCategory } from "@/models/Trait";
import { formatTraitActivationCost, formatTraitCategory, formatTraitUses } from "@/sections/compendium/components/traits/traitFormatters";
import { useSearchSourceIds } from "../referenceStore";
import { FilterChips, FilterSelect, PickerModal } from "./PickerModal";

const CATEGORIES: TraitCategory[] = [
  "BUFF", "CONTROL", "CRAFTING_ITEMS", "DAMAGE", "DEBUFF", "DECLARED", "DEFENSE", "ENVIRONMENTAL",
  "HEALING_RECOVERY", "MAGIC", "MOBILITY", "OTHER", "PASSIVE", "RACIAL", "RESOURCE_ECONOMY", "SKILL",
  "SOCIAL_ROLEPLAY", "STATUS_CONDITIONS", "STEALTH_DECEPTION", "SUMMONING_COMPANIONS", "TRANSFORMATION",
  "TRIGGERED", "UTILITY_EXPLORATION", "WEAPON",
];

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
  const [category, setCategory] = useState<TraitCategory | "">("");
  const [affordable, setAffordable] = useState<"yes" | null>(null);
  return (
    <PickerModal<Trait>
      title="Buy a trait"
      subtitle={`${expRemaining} EXP left`}
      searchPlaceholder="Search traits"
      filters={
        <>
          <FilterSelect
            label="Any category"
            options={CATEGORIES.map((c) => ({ value: c, label: formatTraitCategory(c) }))}
            value={category}
            onChange={setCategory}
          />
          <FilterChips label="Cost" options={[{ value: "yes", label: "Affordable" }]} value={affordable} onChange={setAffordable} />
        </>
      }
      filterKey={`${category}|${affordable}|${sourceIds.join(",")}`}
      load={(page, size, search) =>
        filterTraits(
          {
            categories: category ? [category] : [],
            matchAll: false,
            page,
            size,
            lvl: null,
            lvlOperator: "lte",
            purchaseCost: affordable ? Math.max(0, expRemaining) : null,
            purchaseOperator: "lte",
            tpCost: null,
            tpOperator: "lte",
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
      getId={(t) => t.id}
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
