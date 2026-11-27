import type { RaceSize } from "./Race";

/** Shapes returned by /api/v1/characters (camelCase responses). */

export type CharacterStatus = "DRAFT" | "ACTIVE" | "RETIRED";
export type AttributeName = "BODY" | "STRENGTH" | "SPEED" | "MAGIC";
export type TraitSource = "RACIAL" | "JOB" | "PURCHASED";
export type SkillCategory = "COMBAT" | "LIFE";
export type InventoryLocation =
  | "ARMOR"
  | "LEFT_HAND"
  | "RIGHT_HAND"
  | "BOTH_HANDS"
  | "BAG"
  | "COMPONENTS"
  | "QUEST"
  | "SLOT";

export const ATTRIBUTES: AttributeName[] = ["BODY", "STRENGTH", "SPEED", "MAGIC"];

export interface NamedRef {
  id: string;
  name: string;
}

export interface CharacterSummary {
  id: string;
  name: string;
  status: CharacterStatus;
  jobName: string | null;
  raceNames: string[];
  createdAt: string;
  updatedAt: string;
}

export interface ContentSource {
  id: string;
  name: string;
  description: string;
  kind: "core" | "book" | "campaign";
}

export interface CharacterSheet {
  id: string;
  name: string;
  description: string;
  baseBody: number;
  baseStrength: number;
  baseSpeed: number;
  baseMagic: number;
}

export interface SkillInfo {
  id: string;
  name: string;
  category: SkillCategory;
  baseRating: number;
  weaponTypeId: string | null;
}

export interface CharacterReferenceData {
  sheets: CharacterSheet[];
  skills: SkillInfo[];
}

export interface CharacterRaceDto {
  id: string;
  name: string;
  minSize: RaceSize;
  maxSize: RaceSize | null;
  movement: number | null;
  languages: string;
}

export interface CharacterAttributeDto {
  attribute: AttributeName;
  base: number;
  expSpent: number;
  jobPointsSpent: number;
  bonus: number;
  modifier: number;
  value: number;
}

export interface CharacterSkillDto {
  skillId: string;
  name: string;
  category: SkillCategory;
  rating: number;
  baseRating: number;
  creationAdjustment: number;
  expSpent: number;
  jobPointsSpent: number;
  failures: number;
  permanentModifier: number;
  temporaryModifier: number;
}

export interface CharacterTraitDto {
  traitId: string;
  source: TraitSource;
  raceId: string | null;
  jobId: string | null;
  lvl: number;
  expSpent: number;
  jobPointsSpent: number;
  notes: string;
  /** Uses spent since the last rest. */
  usesSpent: number;
  traitName: string;
  purchaseCost: number | null;
  lvlUpCost: number | null;
  traitDesc: string | null;
  traitUses: number | null;
  mpCost: number | null;
  epCost: number | null;
  tpCost: number | null;
  traitEffect: string | null;
  lvlUpEffect: string | null;
  customizedFields: string[];
}

export interface CharacterPowerDto {
  powerId: string;
  lvl: number;
  expSpent: number;
  notes: string;
  name: string;
  type: string | null;
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
  customizedFields: string[];
}

export interface CharacterCapacitiesDto {
  currentHp: number | null;
  currentWp: number | null;
  currentEp: number | null;
  currentMp: number | null;
  tempHp: number;
  hpBonus: number;
  wpBonus: number;
  epBonus: number;
  mpBonus: number;
  physicalDamageBonus: number;
  magicDamageBonus: number;
  priorityBonus: number;
  movementBonus: number;
}

export interface CharacterBudgetDto {
  totalExp: number;
  expRemaining: number;
  jobPoints: number;
  jobPointsRemaining: number;
  racialTraitCost: number;
}

export interface CharacterDto {
  id: string;
  name: string;
  status: CharacterStatus;
  description: string;
  motive: string;
  ideals: string;
  strengths: string;
  flaws: string;
  history: string;
  importantCharacters: string;
  notes: string;
  characterSheet: NamedRef | null;
  job: NamedRef | null;
  size: RaceSize | null;
  races: CharacterRaceDto[];
  languageRace: NamedRef | null;
  raceLanguages: string | null;
  learnedLanguages: string;
  sourceIds: string[];
  startingExp: number;
  jobPoints: number;
  capacities: CharacterCapacitiesDto;
  budget: CharacterBudgetDto;
  attributes: CharacterAttributeDto[];
  skills: CharacterSkillDto[];
  traits: CharacterTraitDto[];
  powers: CharacterPowerDto[];
  createdAt: string;
  updatedAt: string;
}

export interface CharacterItemDto {
  id: string;
  itemId: string | null;
  name: string;
  customName: string | null;
  compendiumName: string | null;
  itemType: string | null;
  location: InventoryLocation;
  slotId: string | null;
  quantity: number;
  stackLimit: number | null;
  baseStackLimit: number | null;
  stackLimitOverride: number | null;
  attuned: boolean;
  requiresAttunement: boolean;
  chargesRemaining: number | null;
  maxCharges: number | null;
  notes: string;
  sortOrder: number;
}

export interface InventorySlotDto {
  id: string;
  label: string;
  groupName: string | null;
  notes: string;
  sortOrder: number;
  item: CharacterItemDto | null;
}

export interface AttunementSlotDto {
  id: string;
  label: string;
  groupName: string | null;
  notes: string;
  sortOrder: number;
}

export interface CharacterInventoryDto {
  platinum: number;
  gold: number;
  silver: number;
  bronze: number;
  baseAttunementLimit: number;
  stackLimitMultiplier: number;
  notes: string;
  armor: CharacterItemDto | null;
  leftHand: CharacterItemDto | null;
  rightHand: CharacterItemDto | null;
  bothHands: CharacterItemDto | null;
  bag: CharacterItemDto | null;
  components: CharacterItemDto[];
  quest: CharacterItemDto[];
  slots: InventorySlotDto[];
  attunementSlots: AttunementSlotDto[];
}

export interface SavedCharacter {
  character: CharacterDto;
  inventory: CharacterInventoryDto;
}
