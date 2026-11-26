import type { RacePage, RaceSize } from "../models/Race";
import type { Operator } from "./traitApi";
import { authorizedFetch } from "../auth/session";

/** Body sent to POST /races/filter-by-category; null values are ignored by the backend. */
export interface RaceFilterRequest {
  sizes: RaceSize[];
  movement: number | null;
  movementOperator: Operator;
  page: number;
  size: number;
  name: string | null;
  /** Matches races containing a trait whose name includes this text. */
  traitName: string | null;
}


/** Performs a race API request and verifies the response is a page of races. */
async function requestRaces(path: string, init?: RequestInit): Promise<RacePage> {
  const response = await authorizedFetch(`${path}`, init);
  if (!response.ok) {
    throw new Error(`Race API request failed: ${response.status} ${response.statusText}`);
  }

  const result: unknown = await response.json();
  if (result !== null && typeof result === "object" && "content" in result && Array.isArray(result.content)) {
    return result as RacePage;
  }

  throw new Error("Race API returned an unexpected response");
}

/** Fetches one page of all races, sorted by name. */
export function getRaces(page = 0, size = 15): Promise<RacePage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return requestRaces(`/races?${params.toString()}`);
}

/** Fetches one page of races matching the given filters. */
export function filterRaces(filters: RaceFilterRequest, page = 0, size = 15): Promise<RacePage> {
  return requestRaces("/races/filter-by-category", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ ...filters, page, size }),
  });
}
