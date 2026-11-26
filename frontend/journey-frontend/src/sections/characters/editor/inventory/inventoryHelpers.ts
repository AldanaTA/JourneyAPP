import type { InventoryLocation } from "@/models/Character";
import type { DraftInventory, DraftItem, DraftSlot } from "../draft";
import { newKey } from "../draft";
import { stackLimit } from "../rules";

export const PLACE_NAMES: Record<InventoryLocation, string> = {
  ARMOR: "Armor",
  LEFT_HAND: "Left hand",
  RIGHT_HAND: "Right hand",
  BOTH_HANDS: "Both hands",
  BAG: "Bag",
  COMPONENTS: "Magic components",
  QUEST: "Quest items",
  SLOT: "Slot",
};

export function itemName(item: DraftItem): string {
  return item.customName ?? item.compendiumName ?? "Item";
}

export function itemLimit(item: DraftItem, inv: DraftInventory): number | null {
  return stackLimit(item.stackLimitOverride, item.baseStackLimit, inv.stackLimitMultiplier);
}

export function attunementLimit(inv: DraftInventory): number {
  return inv.attunementLimit + inv.attunementSlots.length;
}

export function attunedCount(inv: DraftInventory): number {
  return inv.items.filter((i) => i.attuned).length;
}

/** The item in a place that holds one (armor, a hand, the bag), if any. */
export function itemAt(inv: DraftInventory, location: InventoryLocation): DraftItem | undefined {
  return inv.items.find((i) => i.location === location);
}

export function itemInSlot(inv: DraftInventory, slotKey: string): DraftItem | undefined {
  return inv.items.find((i) => i.location === "SLOT" && i.slotKey === slotKey);
}

/** Whether a place that holds one item is free (a two-handed item fills both hands). */
export function placeIsFree(inv: DraftInventory, location: InventoryLocation, ignoring?: string): boolean {
  const taken = (l: InventoryLocation) => inv.items.some((i) => i.key !== ignoring && i.location === l);
  switch (location) {
    case "LEFT_HAND":
      return !taken("LEFT_HAND") && !taken("BOTH_HANDS");
    case "RIGHT_HAND":
      return !taken("RIGHT_HAND") && !taken("BOTH_HANDS");
    case "BOTH_HANDS":
      return !taken("LEFT_HAND") && !taken("RIGHT_HAND") && !taken("BOTH_HANDS");
    case "ARMOR":
    case "BAG":
      return !taken(location);
    default:
      return true;
  }
}

/** Groups in display order, then the slots without a group. */
export function groupSlots(slots: DraftSlot[]): { group: string | null; slots: DraftSlot[] }[] {
  const groups = new Map<string | null, DraftSlot[]>();
  for (const slot of slots) {
    const list = groups.get(slot.groupName) ?? [];
    list.push(slot);
    groups.set(slot.groupName, list);
  }
  const named = [...groups.entries()].filter(([g]) => g !== null).map(([group, list]) => ({ group, slots: list }));
  const loose = groups.get(null);
  return loose ? [...named, { group: null, slots: loose }] : named;
}

function numberOf(label: string, group: string): number | null {
  const prefix = `${group} `;
  if (!label.startsWith(prefix)) return null;
  const n = Number(label.slice(prefix.length));
  return Number.isInteger(n) && n > 0 ? n : null;
}

/**
 * New slots for a group, numbered on from the highest number it already uses: adding 2 to "Spatial Storage 1..3"
 * makes "Spatial Storage 4" and "Spatial Storage 5".
 */
export function newGroupSlots(existing: DraftSlot[], group: string, count: number): DraftSlot[] {
  const used = existing.filter((s) => s.groupName === group).map((s) => numberOf(s.label, group) ?? 0);
  let next = Math.max(0, ...used) + 1;
  return Array.from({ length: count }, () => ({ key: newKey(), label: `${group} ${next++}`, groupName: group, notes: "", sortOrder: 0 }));
}

/** Renames a group: "Old 2" becomes "New 2"; labels the player wrote themselves are kept. */
export function renameGroup(slots: DraftSlot[], from: string, to: string) {
  for (const slot of slots) {
    if (slot.groupName !== from) continue;
    const n = numberOf(slot.label, from);
    if (n !== null) slot.label = `${to} ${n}`;
    slot.groupName = to;
  }
}

/** Total wealth in bronze (1 platinum = 100 gold = 1000 silver = 10000 bronze). */
export function wealthBronze(inv: DraftInventory): number {
  return inv.platinum * 10_000 + inv.gold * 100 + inv.silver * 10 + inv.bronze;
}
