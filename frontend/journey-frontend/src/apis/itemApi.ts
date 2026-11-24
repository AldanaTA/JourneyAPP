import type { ItemPage, ItemRarity, ItemReferenceData, ItemType } from "../models/Item";

/** Body sent to POST /items/filter-by-category; null/empty values are ignored by the backend. */
export interface ItemFilterRequest {
  itemTypes: ItemType[];
  rarities: ItemRarity[];
  magicOnly: boolean;
  attunementOnly: boolean;
  /** Weapon type names, e.g. "Bow". */
  weaponTypes: string[];
  /** Damage type names, e.g. "Fire". */
  damageTypes: string[];
  page: number;
  size: number;
  name: string | null;
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "/api/v1").replace(/\/$/, "");

/** Performs an item API request and verifies the response is a page of items. */
async function requestItems(path: string, init?: RequestInit): Promise<ItemPage> {
  const response = await fetch(`${API_BASE_URL}${path}`, init);
  if (!response.ok) {
    throw new Error(`Item API request failed: ${response.status} ${response.statusText}`);
  }

  const result: unknown = await response.json();
  if (result !== null && typeof result === "object" && "content" in result && Array.isArray(result.content)) {
    return result as ItemPage;
  }

  throw new Error("Item API returned an unexpected response");
}

/** Fetches one page of all items, sorted by name. */
export function getItems(page = 0, size = 15): Promise<ItemPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return requestItems(`/items?${params.toString()}`);
}

/** Fetches one page of items matching the given filters. */
export function filterItems(filters: ItemFilterRequest, page = 0, size = 15): Promise<ItemPage> {
  return requestItems("/items/filter-by-category", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ ...filters, page, size }),
  });
}

/** Fetches the weapon types, weapon properties, and damage types (for filter options). */
export async function getItemReferenceData(): Promise<ItemReferenceData> {
  const response = await fetch(`${API_BASE_URL}/items/reference-data`);
  if (!response.ok) {
    throw new Error(`Item API request failed: ${response.status} ${response.statusText}`);
  }
  return (await response.json()) as ItemReferenceData;
}
