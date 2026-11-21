import type { Trait, TraitCategory } from "../../../../models/Trait";

/** Turns an enum label like "HEALING_RECOVERY" into readable text like "Healing Recovery". */
export function formatTraitCategory(category: TraitCategory): string {
  return category
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

/** Lists the activation costs that are actually set (> 0), e.g. ["2 MP", "1 TP"]. */
export function formatTraitActivationCost(trait: Trait): string[] {
  return [
    trait.mpCost ? `${trait.mpCost} MP` : null,
    trait.tpCost ? `${trait.tpCost} TP` : null,
    trait.epCost ? `${trait.epCost} EP` : null,
  ].filter((cost): cost is string => cost !== null);
}

/** Describes how often a trait can be used; 0 or missing means it is not use-limited. */
export function formatTraitUses(trait: Trait): string {
  return trait.traitUses && trait.traitUses > 0 ? `${trait.traitUses} use${trait.traitUses === 1 ? "" : "s"}` : "Unlimited";
}

/** Returns the text unless it is empty or the database placeholder "None". */
export function meaningfulText(value: string | null): string | null {
  if (!value) return null;
  const trimmed = value.trim();
  return trimmed === "" || trimmed.toLowerCase() === "none" ? null : trimmed;
}
