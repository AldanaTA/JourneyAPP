/**
 * The character creation rules, worked out in the browser so the editor can show totals as the player edits.
 * They mirror the database views (character_point_budget, character_stats, character_skill_ratings); the server
 * checks the same rules again when the character is saved.
 */
import type { AttributeName, CharacterSheet, SkillInfo } from "@/models/Character";
import { ATTRIBUTES } from "@/models/Character";
import type { RaceSize } from "@/models/Race";
import type { Draft, DraftPower, DraftTrait, PowerFields, TraitFields } from "./draft";

/** A mixed race picks racial traits worth exactly this much EXP. */
export const MIXED_RACE_TRAIT_EXP = 50;
/** Traits stop levelling here. */
export const MAX_TRAIT_LEVEL = 6;
/** The cost of learning a power. */
export const POWER_COST = 10;
/** A creation swap may lower a skill by at most this much. */
export const MAX_SKILL_SWAP = 20;
/** Skill ratings never drop below this. */
export const MIN_SKILL_RATING = 5;

export const SIZE_ORDER: RaceSize[] = ["T", "S", "M", "L", "H", "C"];
export const SIZE_NAMES: Record<RaceSize, string> = { T: "Tiny", S: "Small", M: "Medium", L: "Large", H: "Huge", C: "Colossal" };

export const ATTRIBUTE_NAMES: Record<AttributeName, string> = { BODY: "Body", STRENGTH: "Strength", SPEED: "Speed", MAGIC: "Magic" };

export function effectiveTrait(t: DraftTrait): TraitFields {
  return { ...t.base, ...t.edits };
}

export function effectivePower(p: DraftPower): PowerFields {
  return { ...p.base, ...p.edits };
}

/** EXP to raise a trait one level: its level-up cost, else half its purchase cost. */
export function traitLevelCost(t: DraftTrait): number {
  const fields = effectiveTrait(t);
  return fields.lvlUpCost ?? Math.floor((fields.purchaseCost ?? 0) / 2);
}

/** EXP to raise a power from {@code lvl} to the next level (5 x LVL). */
export function powerLevelCost(lvl: number): number {
  return 5 * lvl;
}

export interface Budget {
  totalExp: number;
  expSpent: number;
  expRemaining: number;
  jobPoints: number;
  jobTraitPoints: number;
  jobPointsRemaining: number;
  racialTraitCost: number;
  combatSwap: number;
  lifeSwap: number;
}

export function budget(d: Draft, skills: SkillInfo[]): Budget {
  const attrs = ATTRIBUTES.map((name) => d.attributes[name]);
  const skillRows = Object.values(d.skills);
  const expSpent =
    attrs.reduce((sum, a) => sum + a.expSpent, 0) +
    skillRows.reduce((sum, s) => sum + s.expSpent, 0) +
    d.traits.reduce((sum, t) => sum + t.expSpent, 0) +
    d.powers.reduce((sum, p) => sum + p.expSpent, 0);
  const jobTraitPoints = d.traits
    .filter((t) => t.source === "JOB")
    .reduce((sum, t) => sum + (effectiveTrait(t).purchaseCost ?? 0), 0);
  const jobOnStats = attrs.reduce((sum, a) => sum + a.jobPointsSpent, 0) + skillRows.reduce((sum, s) => sum + s.jobPointsSpent, 0);
  const category = new Map(skills.map((s) => [s.id, s.category]));
  let combatSwap = 0;
  let lifeSwap = 0;
  for (const [id, s] of Object.entries(d.skills)) {
    if (category.get(id) === "COMBAT") combatSwap += s.creationAdjustment;
    else lifeSwap += s.creationAdjustment;
  }
  const totalExp = d.startingExp + d.awardedExp;
  return {
    totalExp,
    expSpent,
    expRemaining: totalExp - expSpent,
    jobPoints: d.jobPoints,
    jobTraitPoints,
    jobPointsRemaining: d.jobPoints - jobTraitPoints - jobOnStats,
    racialTraitCost: d.traits
      .filter((t) => t.source === "RACIAL")
      .reduce((sum, t) => sum + (effectiveTrait(t).purchaseCost ?? 0), 0),
    combatSwap,
    lifeSwap,
  };
}

export function sheetBase(sheet: CharacterSheet | undefined, name: AttributeName): number {
  if (!sheet) return 0;
  switch (name) {
    case "BODY":
      return sheet.baseBody;
    case "STRENGTH":
      return sheet.baseStrength;
    case "SPEED":
      return sheet.baseSpeed;
    case "MAGIC":
      return sheet.baseMagic;
  }
}

export function attributeValue(d: Draft, sheet: CharacterSheet | undefined, name: AttributeName): number {
  const a = d.attributes[name];
  return sheetBase(sheet, name) + a.expSpent + a.jobPointsSpent + a.bonus + a.modifier;
}

export interface Stats {
  body: number;
  strength: number;
  speed: number;
  magic: number;
  maxHp: number;
  maxWp: number;
  maxEp: number;
  maxMp: number;
  physicalDamage: number;
  magicDamage: number;
  priority: number;
  movementFt: number;
}

const div = (a: number, b: number) => Math.trunc(a / b);

/** The character sheet formulas (as in the character_stats view). */
export function stats(d: Draft, sheet: CharacterSheet | undefined): Stats {
  const body = attributeValue(d, sheet, "BODY");
  const strength = attributeValue(d, sheet, "STRENGTH");
  const speed = attributeValue(d, sheet, "SPEED");
  const magic = attributeValue(d, sheet, "MAGIC");
  const c = d.capacities;
  const movements = d.races.map((r) => r.movement).filter((m): m is number => m !== null);
  return {
    body,
    strength,
    speed,
    magic,
    maxHp: body + c.hpBonus,
    maxWp: div(body, 2) + c.wpBonus,
    maxEp: strength + c.epBonus,
    maxMp: magic + c.mpBonus,
    physicalDamage: div(strength, 15) * 2 + c.physicalDamageBonus,
    magicDamage: div(magic, 15) * 2 + c.magicDamageBonus,
    priority: div(speed, 15) + c.priorityBonus,
    movementFt: (movements.length ? Math.min(...movements) : 30) + div(speed, 15) * 5 + c.movementBonus,
  };
}

export function skillRating(d: Draft, skill: SkillInfo): number {
  const s = d.skills[skill.id];
  if (!s) return Math.max(MIN_SKILL_RATING, skill.baseRating);
  return Math.max(
    MIN_SKILL_RATING,
    skill.baseRating + s.creationAdjustment + s.expSpent + s.jobPointsSpent + s.permanentModifier + s.temporaryModifier,
  );
}

/** The sizes the character can be: within the size range of any of its races. */
export function allowedSizes(d: Draft): RaceSize[] {
  const allowed = new Set<RaceSize>();
  for (const race of d.races) {
    const from = SIZE_ORDER.indexOf(race.minSize);
    const to = SIZE_ORDER.indexOf(race.maxSize ?? race.minSize);
    for (let i = from; i <= to; i++) allowed.add(SIZE_ORDER[i]);
  }
  return SIZE_ORDER.filter((s) => allowed.has(s));
}

/** What still keeps a draft from meeting the creation rules (shown on the Identity tab). */
export function creationProblems(d: Draft, b: Budget): string[] {
  const problems: string[] = [];
  if (!d.name.trim()) problems.push("Give the character a name");
  if (!d.sheetId) problems.push("Choose a character sheet");
  if (d.races.length === 0) problems.push("Choose a race");
  else {
    if (!d.languageRaceId) problems.push("Choose a language set");
    if (!d.size) problems.push("Choose a size");
    if (d.races.length > 1 && b.racialTraitCost !== MIXED_RACE_TRAIT_EXP) {
      problems.push(`Pick racial traits worth ${MIXED_RACE_TRAIT_EXP} EXP (now ${b.racialTraitCost})`);
    }
  }
  if (!d.job) problems.push("Choose a job");
  if (b.expRemaining < 0) problems.push(`EXP is overspent by ${-b.expRemaining}`);
  if (b.jobPointsRemaining < 0) problems.push(`Job points are overspent by ${-b.jobPointsRemaining}`);
  if (b.combatSwap !== 0) problems.push(`Combat skill swaps must balance (now ${b.combatSwap > 0 ? "+" : ""}${b.combatSwap})`);
  if (b.lifeSwap !== 0) problems.push(`Life skill swaps must balance (now ${b.lifeSwap > 0 ? "+" : ""}${b.lifeSwap})`);
  return problems;
}

/** Shows a number with its sign: +2, -1, 0. */
export function signed(n: number): string {
  return n > 0 ? `+${n}` : String(n);
}

/** Effective stack limit of an item in this inventory (null = no limit). */
export function stackLimit(override: number | null, base: number | null, multiplier: number): number | null {
  if (override !== null) return override;
  return base === null ? null : base * multiplier;
}
