import type { ContentKind } from "@/apis/campaignApi";
import type { Creature } from "@/models/Creature";
import type { Item } from "@/models/Item";
import type { Job } from "@/models/Job";
import type { Power } from "@/models/Power";
import type { Race } from "@/models/Race";
import type { Trait } from "@/models/Trait";
import { ITEM_TYPES, formatItemType } from "../../compendium/components/items/itemFormatters";
import { formatTraitCategory } from "../../compendium/components/traits/traitFormatters";

export type ContentTabKind = Extract<ContentKind, "traits" | "powers" | "items" | "creatures" | "races" | "jobs">;

export const TRAIT_CATEGORIES = [
  "BUFF", "CONTROL", "CRAFTING_ITEMS", "DAMAGE", "DEBUFF", "DECLARED", "DEFENSE", "ENVIRONMENTAL",
  "HEALING_RECOVERY", "MAGIC", "MOBILITY", "OTHER", "PASSIVE", "RACIAL", "RESOURCE_ECONOMY", "SKILL",
  "SOCIAL_ROLEPLAY", "STATUS_CONDITIONS", "STEALTH_DECEPTION", "SUMMONING_COMPANIONS", "TRANSFORMATION",
  "TRIGGERED", "UTILITY_EXPLORATION", "WEAPON",
] as const;
export const POWER_CATEGORIES = [
  "ACID", "BLEEDING", "BLESSED", "BUFF", "BLINDED", "BLUDGEONING", "CHARMED", "COLD", "CONTROL", "CURSED", "DAMAGE", "DARK", "DAZED", "DEAFENED", "DEBUFF", "DECLARED", "DEFENSE", "ENFEEBLE", "EXHAUSTION", "FIRE", "FORCE", "FRIGHTENED", "HEALING_RECOVERY", "IMPAIRED", "INCAPACITATED", "INFATUATED", "INSPIRED", "INVISIBLE", "LIGHT", "LIGHTNING", "LIGHT_ATTACK", "MAGIC", "MIGHTY", "MOBILITY", "PETRIFIED", "PIERCING", "POISON", "PRONE", "PSYCHIC", "RESTRAINED", "RUSH", "SILENCED", "SLASHING", "SLUGGISH", "STUNNED", "SUPPRESSED", "STRONG_ATTACK", "SUMMONING_COMPANIONS", "TRIGGERED", "UNCONSCIOUS", "VULNERABLE",
] as const;
export const POWER_TYPES = ["Destruction", "Support", "Sabotage", "Utility"] as const;
export const SIZES = ["T", "S", "M", "L", "H", "C"] as const;
export const SIZE_NAMES: Record<string, string> = { T: "Tiny", S: "Small", M: "Medium", L: "Large", H: "Huge", C: "Colossal" };
export const JOB_CATEGORIES = [
  "Caster", "Companion", "Crafter", "Defender", "Divine", "Generalist", "Half-Caster", "Martial",
  "Nature", "Occult", "Ranged", "Saboteur", "Scout", "Social", "Specialist", "Support",
] as const;

type AnyContent = Trait | Power | Item | Creature | Race | Job;

export interface KindConfig {
  label: string;
  singular: string;
  nameOf: (x: AnyContent) => string;
  summary: (x: AnyContent) => string;
  filterLabel: string;
  filterOptions: { value: string; label: string }[];
  /** The filter-by-category body for this kind (only the campaign's own content). */
  body: (name: string, filter: string, campaignId: string) => Record<string, unknown>;
}

const costs = (x: { mpCost?: number | null; epCost?: number | null; tpCost?: number | null; hpCost?: number | null }) =>
  [x.mpCost ? `${x.mpCost} MP` : null, x.hpCost ? `${x.hpCost} HP` : null, x.epCost ? `${x.epCost} EP` : null, x.tpCost ? `${x.tpCost} TP` : null]
    .filter(Boolean)
    .join(" · ");

export const KINDS: Record<ContentTabKind, KindConfig> = {
  traits: {
    label: "Traits",
    singular: "trait",
    nameOf: (x) => (x as Trait).traitName,
    summary: (x) => {
      const t = x as Trait;
      return [`${t.purchaseCost ?? 0} EXP`, costs(t) || (t.traitUses ? `${t.traitUses} uses` : null)].filter(Boolean).join(" · ");
    },
    filterLabel: "Any category",
    filterOptions: TRAIT_CATEGORIES.map((c) => ({ value: c, label: formatTraitCategory(c) })),
    body: (name, filter, cid) => ({ name: name || null, categories: filter ? [filter] : [], matchAll: false, sourceIds: [cid] }),
  },
  powers: {
    label: "Powers",
    singular: "power",
    nameOf: (x) => (x as Power).name,
    summary: (x) => [(x as Power).type, costs(x as Power)].filter(Boolean).join(" · "),
    filterLabel: "Any type",
    filterOptions: POWER_TYPES.map((t) => ({ value: t, label: t })),
    body: (name, filter, cid) => ({ name: name || null, type: filter || null, categories: [], matchAll: false, sourceIds: [cid] }),
  },
  items: {
    label: "Items",
    singular: "item",
    nameOf: (x) => (x as Item).name,
    summary: (x) => {
      const i = x as Item;
      return [formatItemType(i.itemType), i.rarity.toLowerCase().replace("_", " "), i.requiresAttunement ? "attunement" : null].filter(Boolean).join(" · ");
    },
    filterLabel: "Any type",
    filterOptions: ITEM_TYPES.map((t) => ({ value: t, label: formatItemType(t) })),
    body: (name, filter, cid) => ({
      name: name || null, itemTypes: filter ? [filter] : [], rarities: [], magicOnly: false, attunementOnly: false,
      weaponTypes: [], damageTypes: [], sourceIds: [cid],
    }),
  },
  creatures: {
    label: "Creatures",
    singular: "creature",
    nameOf: (x) => (x as Creature).name,
    summary: (x) => {
      const c = x as Creature;
      return `${c.creatureType} · Threat ${c.threatLvl} · ${c.hp} HP`;
    },
    filterLabel: "Any size",
    filterOptions: SIZES.map((s) => ({ value: s, label: SIZE_NAMES[s] })),
    body: (name, filter, cid) => ({
      name: name || null, creatureTypes: [], sizes: filter ? [filter] : [], weakTo: [], resistantTo: [], immuneTo: [], sourceIds: [cid],
    }),
  },
  races: {
    label: "Races",
    singular: "race",
    nameOf: (x) => (x as Race).raceName,
    summary: (x) => {
      const r = x as Race;
      return `${SIZE_NAMES[r.minRaceSize]}${r.maxRaceSize && r.maxRaceSize !== r.minRaceSize ? `–${SIZE_NAMES[r.maxRaceSize]}` : ""} · ${r.movement} ft · ${r.traits.length} traits`;
    },
    filterLabel: "Any size",
    filterOptions: SIZES.map((s) => ({ value: s, label: SIZE_NAMES[s] })),
    body: (name, filter, cid) => ({ name: name || null, sizes: filter ? [filter] : [], movementOperator: "lte", traitName: null, sourceIds: [cid] }),
  },
  jobs: {
    label: "Jobs",
    singular: "job",
    nameOf: (x) => (x as Job).jobName,
    summary: (x) => {
      const j = x as Job;
      return [j.primaryCategory, `${j.traits.length} traits`].filter(Boolean).join(" · ");
    },
    filterLabel: "Any category",
    filterOptions: JOB_CATEGORIES.map((c) => ({ value: c, label: c })),
    body: (name, filter, cid) => ({ name: name || null, categories: filter ? [filter] : [], matchAll: false, primaryOnly: false, traitName: null, sourceIds: [cid] }),
  },
};
