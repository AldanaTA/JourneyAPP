export type PowerType = "Destruction" | "Support" | "Sabotage" | "Utility";

export type PowerCategory =
    |"ACID"
    |"BLEEDING"
    |"BLESSED"
    |"BUFF"
    |"BLINDED"
    |"BLUDGEONING"
    |"CHARMED"
    |"COLD"
    |"CONTROL"
    |"CURSED"
    |"DAMAGE"
    |"DARK"
    |"DAZED"
    |"DEAFENED"
    |"DEBUFF"
    |"DECLARED"
    |"DEFENSE"
    |"ENFEEBLE"
    |"EXHAUSTION"
    |"FIRE"
    |"FORCE"
    |"FRIGHTENED"
    |"HEALING_RECOVERY"
    |"IMPAIRED"
    |"INCAPACITATED"
    |"INFATUATED"
    |"INSPIRED"
    |"INVISIBLE"
    |"LIGHT"
    |"LIGHTNING"
    |"LIGHT_ATTACK"
    |"MAGIC"
    |"MIGHTY"
    |"MOBILITY"
    |"PETRIFIED"
    |"PIERCING"
    |"POISON"
    |"PRONE"
    |"PSYCHIC"
    |"RESTRAINED"
    |"RUSH"
    |"SILENCED"
    |"SLASHING"
    |"SLUGGISH"
    |"STUNNED"
    |"SUPPRESSED"
    |"STRONG_ATTACK"
    |"SUMMONING_COMPANIONS"
    |"TRIGGERED"
    |"UNCONSCIOUS"
    |"VULNERABLE"
    

export interface Power {
  id: string;
  name: string;
  lvl: number | null;
  type: PowerType;
  tpCost: number | null;
  hpCost: number | null;
  mpCost: number | null;
  epCost: number | null;
  materialComponents: string | null;
  verbal: boolean | null;
  sight: boolean | null;
  somatic: boolean | null;
  isDistinct: boolean | null;
  concentration: boolean | null;
  range: string | null;
  area: string | null;
  duration: string | null;
  effect: string | null;
  empowerEffect: string | null;
  lvlUpEffect: string | null;
  categories: PowerCategory[];
  contentSourceName: string | null;
}

export interface PowerPage {
  content: Power[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}