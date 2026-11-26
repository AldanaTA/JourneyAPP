/**
 * The character as the editor holds it. Every change is made here, in the browser; the server only sees it on
 * Save or autosave (one PUT with the whole character, see toSaveBody).
 */
import type { SaveCharacterBody } from "@/apis/characterApi";
import type {
  AttributeName,
  CharacterCapacitiesDto,
  CharacterDto,
  CharacterInventoryDto,
  CharacterItemDto,
  CharacterPowerDto,
  CharacterStatus,
  CharacterTraitDto,
  InventoryLocation,
  TraitSource,
} from "@/models/Character";
import { ATTRIBUTES } from "@/models/Character";
import type { Item } from "@/models/Item";
import type { Power } from "@/models/Power";
import type { RaceSize } from "@/models/Race";
import type { Trait } from "@/models/Trait";

// ----------------------------
// Traits and powers
// ----------------------------

/** The parts of a trait a player can change for their character. */
export interface TraitFields {
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
}

/** Editor field -> API field name. */
export const TRAIT_FIELD_KEYS: Record<keyof TraitFields, string> = {
  traitName: "trait_name",
  purchaseCost: "purchase_cost",
  lvlUpCost: "lvl_up_cost",
  traitDesc: "trait_desc",
  traitUses: "trait_uses",
  mpCost: "mp_cost",
  epCost: "ep_cost",
  tpCost: "tp_cost",
  traitEffect: "trait_effect",
  lvlUpEffect: "lvl_up_effect",
};

export interface DraftTrait {
  traitId: string;
  source: TraitSource;
  raceId: string | null;
  lvl: number;
  expSpent: number;
  notes: string;
  /** The compendium trait. */
  base: TraitFields;
  /** Only the fields changed for this character. */
  edits: Partial<TraitFields>;
  /**
   * False when the trait came from the server with edits: the compendium value of an edited field is then not
   * known until the compendium trait is fetched (the edit dialog does that).
   */
  baseKnown: boolean;
}

export interface PowerFields {
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
}

export const POWER_FIELD_KEYS: Record<keyof PowerFields, string> = {
  name: "name",
  type: "type",
  tpCost: "tp_cost",
  hpCost: "hp_cost",
  mpCost: "mp_cost",
  epCost: "ep_cost",
  materialComponents: "material_components",
  verbal: "verbal",
  sight: "sight",
  somatic: "somatic",
  isDistinct: "is_distinct",
  concentration: "concentration",
  range: "range",
  area: "area",
  duration: "duration",
  effect: "effect",
  empowerEffect: "empower_effect",
  lvlUpEffect: "lvl_up_effect",
};

export interface DraftPower {
  powerId: string;
  lvl: number;
  expSpent: number;
  notes: string;
  base: PowerFields;
  edits: Partial<PowerFields>;
  baseKnown: boolean;
}

// ----------------------------
// Races and job
// ----------------------------

export interface DraftRace {
  id: string;
  name: string;
  minSize: RaceSize;
  maxSize: RaceSize | null;
  movement: number | null;
  languages: string;
  /** The race's racial traits; null until fetched. */
  traits: Trait[] | null;
}

export interface DraftJob {
  id: string;
  name: string;
  /** The traits the job offers for job points; null until fetched. */
  traits: Trait[] | null;
}

// ----------------------------
// Inventory
// ----------------------------

export interface DraftSlot {
  /** The slot's id on the server, or a new key made by the editor. */
  key: string;
  label: string;
  groupName: string | null;
  notes: string;
  sortOrder: number;
}

export interface DraftItem {
  key: string;
  itemId: string | null;
  customName: string | null;
  compendiumName: string | null;
  itemType: string | null;
  location: InventoryLocation;
  slotKey: string | null;
  quantity: number;
  /** The compendium item's stack limit (before the character's multiplier). */
  baseStackLimit: number | null;
  /** This stack's own limit, when set. */
  stackLimitOverride: number | null;
  attuned: boolean;
  requiresAttunement: boolean;
  chargesRemaining: number | null;
  maxCharges: number | null;
  notes: string;
  sortOrder: number;
}

export interface DraftInventory {
  platinum: number;
  gold: number;
  silver: number;
  bronze: number;
  attunementLimit: number;
  stackLimitMultiplier: number;
  notes: string;
  slots: DraftSlot[];
  attunementSlots: DraftSlot[];
  items: DraftItem[];
}

// ----------------------------
// The character
// ----------------------------

export interface DraftAttribute {
  expSpent: number;
  jobPointsSpent: number;
  bonus: number;
  modifier: number;
}

export interface DraftSkill {
  creationAdjustment: number;
  expSpent: number;
  jobPointsSpent: number;
  failures: number;
  permanentModifier: number;
  temporaryModifier: number;
}

export interface Draft {
  /** Null until the first save creates the character. */
  id: string | null;
  status: CharacterStatus;
  name: string;
  description: string;
  motive: string;
  ideals: string;
  strengths: string;
  flaws: string;
  history: string;
  importantCharacters: string;
  notes: string;
  learnedLanguages: string;
  sourceIds: string[];
  sheetId: string | null;
  size: RaceSize | null;
  races: DraftRace[];
  languageRaceId: string | null;
  job: DraftJob | null;
  startingExp: number;
  /** EXP from awards (read-only here). */
  awardedExp: number;
  jobPoints: number;
  attributes: Record<AttributeName, DraftAttribute>;
  skills: Record<string, DraftSkill>;
  capacities: CharacterCapacitiesDto;
  traits: DraftTrait[];
  powers: DraftPower[];
  inventory: DraftInventory;
}

export const STARTING_EXP = 150;
export const JOB_POINTS = 50;

/** A new key for something the editor creates (slots, items). */
export function newKey(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `k-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

function emptyAttribute(): DraftAttribute {
  return { expSpent: 0, jobPointsSpent: 0, bonus: 0, modifier: 0 };
}

export function emptySkill(): DraftSkill {
  return { creationAdjustment: 0, expSpent: 0, jobPointsSpent: 0, failures: 0, permanentModifier: 0, temporaryModifier: 0 };
}

/** A brand-new character, before it is saved. */
export function newDraft(sourceIds: string[]): Draft {
  return {
    id: null,
    status: "DRAFT",
    name: "",
    description: "",
    motive: "",
    ideals: "",
    strengths: "",
    flaws: "",
    history: "",
    importantCharacters: "",
    notes: "",
    learnedLanguages: "",
    sourceIds,
    sheetId: null,
    size: null,
    races: [],
    languageRaceId: null,
    job: null,
    startingExp: STARTING_EXP,
    awardedExp: 0,
    jobPoints: JOB_POINTS,
    attributes: { BODY: emptyAttribute(), STRENGTH: emptyAttribute(), SPEED: emptyAttribute(), MAGIC: emptyAttribute() },
    skills: {},
    capacities: {
      currentHp: null,
      currentWp: null,
      currentEp: null,
      currentMp: null,
      tempHp: 0,
      hpBonus: 0,
      wpBonus: 0,
      epBonus: 0,
      mpBonus: 0,
      physicalDamageBonus: 0,
      magicDamageBonus: 0,
      priorityBonus: 0,
      movementBonus: 0,
    },
    traits: [],
    powers: [],
    inventory: {
      platinum: 0,
      gold: 0,
      silver: 0,
      bronze: 0,
      attunementLimit: 3,
      stackLimitMultiplier: 1,
      notes: "",
      slots: [],
      attunementSlots: [],
      items: [],
    },
  };
}

// ----------------------------
// From compendium entries
// ----------------------------

export function traitFields(t: Trait | CharacterTraitDto): TraitFields {
  return {
    traitName: t.traitName,
    purchaseCost: t.purchaseCost,
    lvlUpCost: t.lvlUpCost,
    traitDesc: t.traitDesc,
    traitUses: t.traitUses,
    mpCost: t.mpCost,
    epCost: t.epCost,
    tpCost: t.tpCost,
    traitEffect: t.traitEffect,
    lvlUpEffect: t.lvlUpEffect,
  };
}

export function powerFields(p: Power | CharacterPowerDto): PowerFields {
  return {
    name: p.name,
    type: p.type,
    tpCost: p.tpCost,
    hpCost: p.hpCost,
    mpCost: p.mpCost,
    epCost: p.epCost,
    materialComponents: p.materialComponents,
    verbal: p.verbal,
    sight: p.sight,
    somatic: p.somatic,
    isDistinct: p.isDistinct,
    concentration: p.concentration,
    range: p.range,
    area: p.area,
    duration: p.duration,
    effect: p.effect,
    empowerEffect: p.empowerEffect,
    lvlUpEffect: p.lvlUpEffect,
  };
}

/** A compendium trait added to the character. */
export function draftTrait(trait: Trait, source: TraitSource, raceId: string | null, expSpent: number): DraftTrait {
  return { traitId: trait.id, source, raceId, lvl: 1, expSpent, notes: "", base: traitFields(trait), edits: {}, baseKnown: true };
}

export function draftPower(power: Power, expSpent: number): DraftPower {
  return { powerId: power.id, lvl: 1, expSpent, notes: "", base: powerFields(power), edits: {}, baseKnown: true };
}

/** A compendium item (or a custom one, with no item) placed somewhere in the inventory. */
export function draftItem(item: Item | null, location: InventoryLocation, slotKey: string | null, customName?: string): DraftItem {
  return {
    key: newKey(),
    itemId: item?.id ?? null,
    customName: item ? null : customName?.trim() || "Item",
    compendiumName: item?.name ?? null,
    itemType: item?.itemType ?? null,
    location,
    slotKey: location === "SLOT" ? slotKey : null,
    quantity: 1,
    baseStackLimit: item?.stackLimit ?? null,
    stackLimitOverride: null,
    attuned: false,
    requiresAttunement: item?.requiresAttunement ?? false,
    chargesRemaining: item?.charges ?? null,
    maxCharges: item?.charges ?? null,
    notes: "",
    sortOrder: 0,
  };
}

// ----------------------------
// From the server
// ----------------------------

function editsFrom<F extends object>(dto: object, customized: string[], keys: Record<keyof F, string>): Partial<F> {
  const edits: Partial<F> = {};
  for (const field of Object.keys(keys) as (keyof F)[]) {
    if (customized.includes(keys[field])) {
      edits[field] = (dto as Record<keyof F, F[keyof F]>)[field];
    }
  }
  return edits;
}

function itemFrom(dto: CharacterItemDto): DraftItem {
  return {
    key: dto.id,
    itemId: dto.itemId,
    customName: dto.customName,
    compendiumName: dto.compendiumName,
    itemType: dto.itemType,
    location: dto.location,
    slotKey: dto.slotId,
    quantity: dto.quantity,
    baseStackLimit: dto.baseStackLimit,
    stackLimitOverride: dto.stackLimitOverride,
    attuned: dto.attuned,
    requiresAttunement: dto.requiresAttunement,
    chargesRemaining: dto.chargesRemaining,
    maxCharges: dto.maxCharges,
    notes: dto.notes,
    sortOrder: dto.sortOrder,
  };
}

/**
 * The editor's version of a saved character. {@code previous} (the draft that was just saved) lends what the
 * server does not send back: the races' and job's trait lists and the compendium versions of edited fields.
 */
export function draftFromServer(c: CharacterDto, inv: CharacterInventoryDto, previous?: Draft | null): Draft {
  const oldRaces = new Map(previous?.races.map((r) => [r.id, r]) ?? []);
  const oldTraits = new Map(previous?.traits.map((t) => [t.traitId, t]) ?? []);
  const oldPowers = new Map(previous?.powers.map((p) => [p.powerId, p]) ?? []);

  const attributes = Object.fromEntries(ATTRIBUTES.map((name) => [name, emptyAttribute()])) as Record<AttributeName, DraftAttribute>;
  for (const a of c.attributes) {
    attributes[a.attribute] = { expSpent: a.expSpent, jobPointsSpent: a.jobPointsSpent, bonus: a.bonus, modifier: a.modifier };
  }
  const skills: Record<string, DraftSkill> = {};
  for (const s of c.skills) {
    skills[s.skillId] = {
      creationAdjustment: s.creationAdjustment,
      expSpent: s.expSpent,
      jobPointsSpent: s.jobPointsSpent,
      failures: s.failures,
      permanentModifier: s.permanentModifier,
      temporaryModifier: s.temporaryModifier,
    };
  }

  const items = [inv.armor, inv.leftHand, inv.rightHand, inv.bothHands, inv.bag, ...inv.components, ...inv.quest,
    ...inv.slots.map((s) => s.item)]
    .filter((item): item is CharacterItemDto => item !== null)
    .map(itemFrom);

  return {
    id: c.id,
    status: c.status,
    name: c.name,
    description: c.description,
    motive: c.motive,
    ideals: c.ideals,
    strengths: c.strengths,
    flaws: c.flaws,
    history: c.history,
    importantCharacters: c.importantCharacters,
    notes: c.notes,
    learnedLanguages: c.learnedLanguages,
    sourceIds: c.sourceIds,
    sheetId: c.characterSheet?.id ?? null,
    size: c.size,
    races: c.races.map((r) => ({
      id: r.id,
      name: r.name,
      minSize: r.minSize,
      maxSize: r.maxSize,
      movement: r.movement,
      languages: r.languages,
      traits: oldRaces.get(r.id)?.traits ?? null,
    })),
    languageRaceId: c.languageRace?.id ?? null,
    job: c.job
      ? { id: c.job.id, name: c.job.name, traits: previous?.job?.id === c.job.id ? previous.job.traits : null }
      : null,
    startingExp: c.startingExp,
    awardedExp: c.budget.totalExp - c.startingExp,
    jobPoints: c.jobPoints,
    attributes,
    skills,
    capacities: { ...c.capacities },
    traits: c.traits.map((t) => {
      const old = oldTraits.get(t.traitId);
      const edits = editsFrom<TraitFields>(t, t.customizedFields, TRAIT_FIELD_KEYS);
      const baseKnown = t.customizedFields.length === 0 || (old?.baseKnown ?? false);
      return {
        traitId: t.traitId,
        source: t.source,
        raceId: t.raceId,
        lvl: t.lvl,
        expSpent: t.expSpent,
        notes: t.notes,
        base: old?.baseKnown ? old.base : traitFields(t),
        edits,
        baseKnown,
      };
    }),
    powers: c.powers.map((p) => {
      const old = oldPowers.get(p.powerId);
      return {
        powerId: p.powerId,
        lvl: p.lvl,
        expSpent: p.expSpent,
        notes: p.notes,
        base: old?.baseKnown ? old.base : powerFields(p),
        edits: editsFrom<PowerFields>(p, p.customizedFields, POWER_FIELD_KEYS),
        baseKnown: p.customizedFields.length === 0 || (old?.baseKnown ?? false),
      };
    }),
    inventory: {
      platinum: inv.platinum,
      gold: inv.gold,
      silver: inv.silver,
      bronze: inv.bronze,
      attunementLimit: inv.baseAttunementLimit,
      stackLimitMultiplier: inv.stackLimitMultiplier,
      notes: inv.notes,
      slots: inv.slots.map((s) => ({ key: s.id, label: s.label, groupName: s.groupName, notes: s.notes, sortOrder: s.sortOrder })),
      attunementSlots: inv.attunementSlots.map((s) => ({
        key: s.id,
        label: s.label,
        groupName: s.groupName,
        notes: s.notes,
        sortOrder: s.sortOrder,
      })),
      items,
    },
  };
}

// ----------------------------
// To the server
// ----------------------------

function apiEdits<F extends object>(edits: Partial<F>, keys: Record<keyof F, string>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const field of Object.keys(edits) as (keyof F)[]) {
    if (edits[field] !== undefined) out[keys[field]] = edits[field];
  }
  return out;
}

/** The body of the one save request. */
export function toSaveBody(d: Draft): SaveCharacterBody {
  const cap = d.capacities;
  const body: SaveCharacterBody = {
    name: d.name.trim() || "Unnamed character",
    description: d.description,
    motive: d.motive,
    ideals: d.ideals,
    strengths: d.strengths,
    flaws: d.flaws,
    learned_languages: d.learnedLanguages,
    history: d.history,
    important_characters: d.importantCharacters,
    notes: d.notes,
    source_ids: d.sourceIds,
    race_ids: d.races.map((r) => r.id),
    language_race_id: d.languageRaceId,
    job_id: d.job?.id ?? "",
    temp_hp: cap.tempHp,
    hp_bonus: cap.hpBonus,
    wp_bonus: cap.wpBonus,
    ep_bonus: cap.epBonus,
    mp_bonus: cap.mpBonus,
    physical_damage_bonus: cap.physicalDamageBonus,
    magic_damage_bonus: cap.magicDamageBonus,
    priority_bonus: cap.priorityBonus,
    movement_bonus: cap.movementBonus,
    attributes: ATTRIBUTES.map((name) => ({
      attribute: name,
      exp_spent: d.attributes[name].expSpent,
      job_points_spent: d.attributes[name].jobPointsSpent,
      bonus: d.attributes[name].bonus,
      modifier: d.attributes[name].modifier,
    })),
    skills: Object.entries(d.skills).map(([skillId, s]) => ({
      skill_id: skillId,
      creation_adjustment: s.creationAdjustment,
      exp_spent: s.expSpent,
      job_points_spent: s.jobPointsSpent,
      failures: s.failures,
      permanent_modifier: s.permanentModifier,
      temporary_modifier: s.temporaryModifier,
    })),
    traits: d.traits.map((t) => ({
      trait_id: t.traitId,
      source: t.source,
      race_id: t.raceId,
      lvl: t.lvl,
      exp_spent: t.expSpent,
      notes: t.notes,
      edits: apiEdits(t.edits, TRAIT_FIELD_KEYS),
    })),
    powers: d.powers.map((p) => ({
      power_id: p.powerId,
      lvl: p.lvl,
      exp_spent: p.expSpent,
      notes: p.notes,
      edits: apiEdits(p.edits, POWER_FIELD_KEYS),
    })),
    inventory: {
      platinum: d.inventory.platinum,
      gold: d.inventory.gold,
      silver: d.inventory.silver,
      bronze: d.inventory.bronze,
      attunement_limit: d.inventory.attunementLimit,
      stack_limit_multiplier: d.inventory.stackLimitMultiplier,
      notes: d.inventory.notes,
      slots: d.inventory.slots.map((s, i) => ({ key: s.key, label: s.label, group_name: s.groupName, notes: s.notes, sort_order: i })),
      attunement_slots: d.inventory.attunementSlots.map((s, i) => ({
        key: s.key,
        label: s.label,
        group_name: s.groupName,
        notes: s.notes,
        sort_order: i,
      })),
      items: d.inventory.items.map((item, i) => ({
        item_id: item.itemId,
        custom_name: item.customName?.trim() ? item.customName.trim() : item.itemId ? null : "Item",
        location: item.location,
        slot_key: item.slotKey,
        quantity: item.quantity,
        stack_limit: item.stackLimitOverride,
        attuned: item.attuned,
        charges_remaining: item.chargesRemaining,
        notes: item.notes,
        sort_order: i,
      })),
    },
  };
  if (d.sheetId) body.character_sheet_id = d.sheetId;
  if (d.size) body.size = d.size;
  if (cap.currentHp !== null) body.current_hp = cap.currentHp;
  if (cap.currentWp !== null) body.current_wp = cap.currentWp;
  if (cap.currentEp !== null) body.current_ep = cap.currentEp;
  if (cap.currentMp !== null) body.current_mp = cap.currentMp;
  return body;
}
