import type { Power, PowerCategory } from "../../../../models/Power";

/** Turns an enum label like "STRONG_ATTACK" into readable text like "Strong Attack". */
export function formatPowerCategory(category: PowerCategory): string {
  return category
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

/** Lists the resource costs that are set, e.g. ["2 MP", "1 TP"]. */
export function formatPowerCost(power: Power): string[] {
  return [
    power.mpCost !== null ? `${power.mpCost} MP` : null,
    power.tpCost !== null ? `${power.tpCost} TP` : null,
    power.hpCost !== null ? `${power.hpCost} HP` : null,
    power.epCost !== null ? `${power.epCost} EP` : null,
  ].filter((cost): cost is string => cost !== null);
}

/** Returns the text unless it is empty or the database placeholder "None". */
export function meaningfulText(value: string | null): string | null {
  if (!value) return null;
  const trimmed = value.trim();
  return trimmed === "" || trimmed.toLowerCase() === "none" ? null : trimmed;
}
