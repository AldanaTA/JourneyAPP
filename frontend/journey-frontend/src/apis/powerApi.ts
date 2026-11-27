import type { PowerPage, PowerType, PowerCategory } from "../models/Power";
import { authorizedFetch } from "../auth/session";

export type Operator = "lte" | "lt" | "eq" | "gt" | "gte";

export interface PowerFilterRequest {
  type: PowerType | null;
  categories: PowerCategory[];
  matchAll: boolean;
  page: number;
  size: number;
  tpCost: number | null;
  tpOperator: Operator;
  /** The power's level (e.g. LVL 3 or below). */
  lvl?: number | null;
  lvlOperator?: Operator;
  hpCost: number | null;
  hpOperator: Operator;
  mpCost: number | null;
  mpOperator: Operator;
  epCost: number | null;
  epOperator: Operator;
  name: string | null;
  /** Only content from these sources (e.g. a character's chosen books); omitted = every source. */
  sourceIds?: string[];
}


async function requestPowers(path: string, init?: RequestInit): Promise<PowerPage> {
  const response = await authorizedFetch(`${path}`, init);
  if (!response.ok) {
    throw new Error(`Power API request failed: ${response.status} ${response.statusText}`);
  }

  const result: unknown = await response.json();
  if (result !== null && typeof result === "object" && "content" in result && Array.isArray(result.content)) {
    return result as PowerPage;
  }

  throw new Error("Power API returned an unexpected response");
}

export function getPowers(page = 0, size = 15): Promise<PowerPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return requestPowers(`/powers?${params.toString()}`);
}

export function filterPowers(filters: PowerFilterRequest, page = 0, size = 15): Promise<PowerPage> {
  return requestPowers("/powers/filter-by-category", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ ...filters, page, size }),
  });
}
