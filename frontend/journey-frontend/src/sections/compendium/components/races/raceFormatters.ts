import type { Race, RaceSize } from "../../../../models/Race";

const SIZE_LABELS: Record<RaceSize, string> = {
  T: "Tiny",
  S: "Small",
  M: "Medium",
  L: "Large",
  H: "Huge",
  C: "Colossal",
};

/** Every race size in ascending order, for filter options. */
export const RACE_SIZES: RaceSize[] = ["T", "S", "M", "L", "H", "C"];

/** Turns a size code like "M" into readable text like "Medium". */
export function formatRaceSize(size: RaceSize): string {
  return SIZE_LABELS[size] ?? size;
}

/** Formats a race's size, or its range when it has one, e.g. "Medium" or "Small - Medium". */
export function formatRaceSizeRange(race: Race): string {
  return race.maxRaceSize
    ? `${formatRaceSize(race.minRaceSize)} - ${formatRaceSize(race.maxRaceSize)}`
    : formatRaceSize(race.minRaceSize);
}

/** Formats the base movement, e.g. "30 ft". */
export function formatRaceMovement(race: Race): string {
  return `${race.movement} ft`;
}

/** Describes how many traits a race has, e.g. "3 traits". */
export function formatRaceTraitCount(race: Race): string {
  return `${race.traits.length} trait${race.traits.length === 1 ? "" : "s"}`;
}
