import type { TraitCategory, TraitPage } from "../models/Trait";
import { authorizedFetch } from "../auth/session";

export type Operator = "lte" | "lt" | "eq" | "gt" | "gte";

/** Body sent to POST /traits/filter-by-category; null values are ignored by the backend. */
export interface TraitFilterRequest {
  categories: TraitCategory[];
  matchAll: boolean;
  page: number;
  size: number;
  lvl: number | null;
  lvlOperator: Operator;
  purchaseCost: number | null;
  purchaseOperator: Operator;
  tpCost: number | null;
  tpOperator: Operator;
  mpCost: number | null;
  mpOperator: Operator;
  epCost: number | null;
  epOperator: Operator;
  name: string | null;
}


/** Performs a trait API request and verifies the response is a page of traits. */
async function requestTraits(path: string, init?: RequestInit): Promise<TraitPage> {
  const response = await authorizedFetch(`${path}`, init);
  if (!response.ok) {
    throw new Error(`Trait API request failed: ${response.status} ${response.statusText}`);
  }

  const result: unknown = await response.json();
  if (result !== null && typeof result === "object" && "content" in result && Array.isArray(result.content)) {
    return result as TraitPage;
  }

  throw new Error("Trait API returned an unexpected response");
}

/** Fetches one page of all traits, sorted by name. */
export function getTraits(page = 0, size = 15): Promise<TraitPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return requestTraits(`/traits?${params.toString()}`);
}

/** Fetches one page of traits matching the given filters. */
export function filterTraits(filters: TraitFilterRequest, page = 0, size = 15): Promise<TraitPage> {
  return requestTraits("/traits/filter-by-category", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ ...filters, page, size }),
  });
}
