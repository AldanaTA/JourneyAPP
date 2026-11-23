/** Every category a trait can be tagged with; mirrors the backend TraitCategory enum. */
export type TraitCategory =
  | "BUFF"
  | "CONTROL"
  | "CRAFTING_ITEMS"
  | "DAMAGE"
  | "DEBUFF"
  | "DECLARED"
  | "DEFENSE"
  | "ENVIRONMENTAL"
  | "HEALING_RECOVERY"
  | "MAGIC"
  | "MOBILITY"
  | "OTHER"
  | "PASSIVE"
  | "RACIAL"
  | "RESOURCE_ECONOMY"
  | "SKILL"
  | "SOCIAL_ROLEPLAY"
  | "STATUS_CONDITIONS"
  | "STEALTH_DECEPTION"
  | "SUMMONING_COMPANIONS"
  | "TRANSFORMATION"
  | "TRIGGERED"
  | "UTILITY_EXPLORATION"
  | "WEAPON";

/** A single trait as returned by the /api/v1/traits endpoints. */
export interface Trait {
  id: string;
  traitName: string;
  lvl: number | null;
  purchaseCost: number | null;
  lvlUpCost: number | null;
  traitDesc: string | null;
  traitUses: number | null;
  mpCost: number | null;
  epCost: number | null;
  tpCost: number | null;
  traitEffect: string | null;
  lvlUpEffect: string | null;
  categories: TraitCategory[];
  contentSourceName: string | null;
}

/** A Spring Data page of traits. */
export interface TraitPage {
  content: Trait[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
