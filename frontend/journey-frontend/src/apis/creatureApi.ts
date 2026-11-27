import type { CreaturePage, CreatureTypeInfo, StatusConditionInfo } from "../models/Creature";
import type { RaceSize } from "../models/Race";
import { authorizedFetch } from "../auth/session";

/** Body sent to POST /creatures/filter-by-category; null/empty values are ignored by the backend. */
export interface CreatureFilterRequest {
  /** Creature type names, matched across sources. */
  creatureTypes: string[];
  minThreatLvl: number | null;
  maxThreatLvl: number | null;
  /** Matches creatures that can be any of these sizes (inside their size range). */
  sizes: RaceSize[];
  /** Damage type or status condition names. */
  weakTo: string[];
  resistantTo: string[];
  immuneTo: string[];
  page: number;
  size: number;
  name: string | null;
}


/** Performs a creature API request and verifies the response is a page of creatures. */
async function requestCreatures(path: string, init?: RequestInit): Promise<CreaturePage> {
  const response = await authorizedFetch(`${path}`, init);
  if (!response.ok) {
    throw new Error(`Creature API request failed: ${response.status} ${response.statusText}`);
  }

  const result: unknown = await response.json();
  if (result !== null && typeof result === "object" && "content" in result && Array.isArray(result.content)) {
    return result as CreaturePage;
  }

  throw new Error("Creature API returned an unexpected response");
}

/** Fetches one page of all creatures, sorted by name. */
export function getCreatures(page = 0, size = 15): Promise<CreaturePage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return requestCreatures(`/creatures?${params.toString()}`);
}

/** Fetches one page of creatures matching the given filters. */
export function filterCreatures(filters: CreatureFilterRequest, page = 0, size = 15): Promise<CreaturePage> {
  return requestCreatures("/creatures/filter-by-category", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ ...filters, page, size }),
  });
}

/** Fetches every creature type (all sources), for filter options. */
/** Creature types: the official ones, or those of one content source (e.g. a campaign's own copies). */
export async function getCreatureTypes(contentSourceId?: string): Promise<CreatureTypeInfo[]> {
  const response = await authorizedFetch(`/creatures/types${contentSourceId ? `?contentSourceId=${encodeURIComponent(contentSourceId)}` : ""}`);
  if (!response.ok) {
    throw new Error(`Creature API request failed: ${response.status} ${response.statusText}`);
  }
  return (await response.json()) as CreatureTypeInfo[];
}

/** Fetches every status condition with its rules text (for filter options and tooltips). */
export async function getStatusConditions(): Promise<StatusConditionInfo[]> {
  const response = await authorizedFetch(`/status-conditions`);
  if (!response.ok) {
    throw new Error(`Status condition API request failed: ${response.status} ${response.statusText}`);
  }
  return (await response.json()) as StatusConditionInfo[];
}
