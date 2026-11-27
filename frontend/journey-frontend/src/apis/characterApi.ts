import { authorizedFetch, errorMessage } from "../auth/session";
import type {
  CharacterDto,
  CharacterInventoryDto,
  CharacterReferenceData,
  CharacterSummary,
  ContentSource,
  SavedCharacter,
} from "../models/Character";
import type { Item } from "../models/Item";
import type { Job } from "../models/Job";
import type { Power } from "../models/Power";
import type { Race } from "../models/Race";
import type { Trait } from "../models/Trait";

/** Sends a JSON request and returns the parsed body; throws with the API's message on failure. */
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await authorizedFetch(path, {
    ...init,
    headers: init.body ? { "Content-Type": "application/json", ...init.headers } : init.headers,
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export function listCharacters(signal?: AbortSignal): Promise<CharacterSummary[]> {
  return request("/characters", { signal });
}

export function getCharacter(id: string, signal?: AbortSignal): Promise<CharacterDto> {
  return request(`/characters/${encodeURIComponent(id)}`, { signal });
}

export function getCharacterInventory(id: string, signal?: AbortSignal): Promise<CharacterInventoryDto> {
  return request(`/characters/${encodeURIComponent(id)}/inventory`, { signal });
}

export function deleteCharacter(id: string): Promise<void> {
  return request(`/characters/${encodeURIComponent(id)}`, { method: "DELETE" });
}

/** Creates an empty draft; the editor follows up with {@link saveCharacter}. */
export function createCharacter(name: string, sourceIds: string[]): Promise<CharacterDto> {
  return request("/characters", { method: "POST", body: JSON.stringify({ name, source_ids: sourceIds }) });
}

/** Saves the whole character in one request (see SaveCharacterRequest on the API). */
export function saveCharacter(id: string, body: SaveCharacterBody): Promise<SavedCharacter> {
  return request(`/characters/${encodeURIComponent(id)}`, { method: "PUT", body: JSON.stringify(body) });
}

let referenceData: Promise<CharacterReferenceData> | null = null;

/** Character sheets and skills; fetched once per page load, since they rarely change. */
export function getCharacterReferenceData(): Promise<CharacterReferenceData> {
  referenceData ??= request<CharacterReferenceData>("/characters/reference-data").catch((error: unknown) => {
    referenceData = null;
    throw error;
  });
  return referenceData;
}

export function getContentSources(): Promise<ContentSource[]> {
  return request("/content-sources");
}

export function getRace(id: string): Promise<Race> {
  return request(`/races/${encodeURIComponent(id)}`);
}

export function getJob(id: string): Promise<Job> {
  return request(`/jobs/${encodeURIComponent(id)}`);
}

export function getTrait(id: string): Promise<Trait> {
  return request(`/traits/${encodeURIComponent(id)}`);
}

export function getPower(id: string): Promise<Power> {
  return request(`/powers/${encodeURIComponent(id)}`);
}

/** Body of PUT /characters/{id} (snake_case, like the API's other requests). */
export interface SaveCharacterBody {
  name: string;
  description: string;
  motive: string;
  ideals: string;
  strengths: string;
  flaws: string;
  learned_languages: string;
  history: string;
  important_characters: string;
  notes: string;
  character_sheet_id?: string;
  size?: string;
  source_ids: string[];
  race_ids: string[];
  language_race_id: string | null;
  job_id: string;
  current_hp?: number;
  current_wp?: number;
  current_ep?: number;
  current_mp?: number;
  temp_hp: number;
  hp_bonus: number;
  wp_bonus: number;
  ep_bonus: number;
  mp_bonus: number;
  physical_damage_bonus: number;
  magic_damage_bonus: number;
  priority_bonus: number;
  movement_bonus: number;
  attributes: {
    attribute: string;
    exp_spent: number;
    job_points_spent: number;
    bonus: number;
    modifier: number;
  }[];
  skills: {
    skill_id: string;
    creation_adjustment: number;
    exp_spent: number;
    job_points_spent: number;
    failures: number;
    permanent_modifier: number;
    temporary_modifier: number;
  }[];
  traits: {
    trait_id: string;
    source: string;
    race_id: string | null;
    lvl: number;
    exp_spent: number;
    notes: string;
    edits: Record<string, unknown>;
    uses_spent: number;
  }[];
  powers: { power_id: string; lvl: number; exp_spent: number; notes: string; edits: Record<string, unknown> }[];
  inventory: {
    platinum: number;
    gold: number;
    silver: number;
    bronze: number;
    attunement_limit: number;
    stack_limit_multiplier: number;
    notes: string;
    slots: { key: string; label: string; group_name: string | null; notes: string; sort_order: number }[];
    attunement_slots: { key: string; label: string; group_name: string | null; notes: string; sort_order: number }[];
    items: {
      item_id: string | null;
      custom_name: string | null;
      location: string;
      slot_key: string | null;
      quantity: number;
      stack_limit: number | null;
      attuned: boolean;
      charges_remaining: number | null;
      notes: string;
      sort_order: number;
    }[];
  };
}

/** Finishes character creation: the draft becomes active if it meets every creation rule. */
export function finalizeCharacter(id: string): Promise<CharacterDto> {
  return request(`/characters/${encodeURIComponent(id)}/finalize`, { method: "POST" });
}

export function getItem(id: string): Promise<Item> {
  return request(`/items/${encodeURIComponent(id)}`);
}
