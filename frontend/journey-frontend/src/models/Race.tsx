import type { Trait } from "./Trait";

/** Creature size of a race; mirrors the backend RaceSize enum (Postgres `size`). */
export type RaceSize = "T" | "S" | "M" | "L" | "H" | "C";

/** A single race as returned by the /api/v1/races endpoints. Traits are in the race's display order. */
export interface Race {
  id: string;
  raceName: string;
  /** The race's size, or the smallest size of its range. */
  minRaceSize: RaceSize;
  /** The largest size of the race's range; null when the race has a single size. */
  maxRaceSize: RaceSize | null;
  /** Base movement in feet. */
  movement: number;
  languages: string;
  raceDescription: string | null;
  traits: Trait[];
  contentSourceName: string | null;
}

/** A Spring Data page of races. */
export interface RacePage {
  content: Race[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
