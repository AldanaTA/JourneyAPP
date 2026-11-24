import type { DamageTypeInfo, WeaponPropertyInfo } from "./Item";
import type { Power } from "./Power";
import type { RaceSize } from "./Race";
import type { Trait } from "./Trait";

/** Whether a creature is immune, resistant, or weak to something; mirrors the backend AugmentKind enum. */
export type AugmentKind = "IMMUNE" | "RESISTANT" | "WEAK";

/** When an ability applies; mirrors the backend AbilityTiming enum. */
export type AbilityTiming = "PASSIVE" | "TRIGGERED" | "DECLARED";

/** A listed skill rating; unlisted skills are 40%. */
export interface CreatureSkill {
  skill: string;
  rating: number;
}

/** A status condition and its rules text (paragraphs separated by blank lines). */
export interface StatusConditionInfo {
  name: string;
  description: string;
}

/** An immunity, resistance, or weakness to exactly one damage type or status condition. */
export interface CreatureAugment {
  kind: AugmentKind;
  /** Name of the damage type or status condition. */
  target: string;
  damageType: DamageTypeInfo | null;
  statusCondition: StatusConditionInfo | null;
}

/** An ability with the creature's own (condensed) text and, when linked, the full game trait or power. */
export interface CreatureAbility {
  timing: AbilityTiming;
  name: string;
  effect: string;
  lvl: number | null;
  uses: number | null;
  mpCost: number | null;
  epCost: number | null;
  tpCost: number | null;
  trait: Trait | null;
  power: Power | null;
}

/** A declared attack. */
export interface CreatureAttack {
  name: string;
  skill: string;
  rangeFt: number | null;
  damageDice: string;
  damageBonus: string | null;
  damageType: DamageTypeInfo;
  mag: number | null;
  properties: WeaponPropertyInfo[];
}

/** A single creature as returned by the /api/v1/creatures endpoints. */
export interface Creature {
  id: string;
  name: string;
  creatureType: string;
  threatLvl: number;
  /** The creature's size, or the smallest size of its range. */
  minSize: RaceSize;
  /** The largest size of its range; null for a single size. */
  maxSize: RaceSize | null;
  description: string | null;
  languages: string | null;
  lore: string | null;
  hp: number;
  wp: number | null;
  mp: number;
  ep: number;
  prio: number;
  /** MVM in feet. */
  movement: number;
  pd: number;
  md: number;
  skills: CreatureSkill[];
  augments: CreatureAugment[];
  abilities: CreatureAbility[];
  attacks: CreatureAttack[];
  contentSourceName: string | null;
}

/** A Spring Data page of creatures. */
export interface CreaturePage {
  content: Creature[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** A creature type and the content source that defines it. */
export interface CreatureTypeInfo {
  id: string;
  name: string;
  description: string | null;
  contentSourceName: string | null;
}
