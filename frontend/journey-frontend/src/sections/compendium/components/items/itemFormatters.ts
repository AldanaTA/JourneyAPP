import type { Item, ItemRarity, ItemType } from "../../../../models/Item";

/** Every item type, in the order the book presents them; used for filter options. */
export const ITEM_TYPES: ItemType[] = [
  "WEAPON",
  "ARMOR",
  "SHIELD",
  "AMMUNITION",
  "POTION",
  "ACCESSORY",
  "MAGIC_ITEM",
  "BAG",
  "MED_KIT",
  "SKILL_BOOK",
  "TOOL",
  "ALCHEMY_INGREDIENT",
  "COOKING_INGREDIENT",
  "GEM",
  "METAL",
  "MISCELLANEOUS",
];

/** Every rarity, lowest to highest. */
export const ITEM_RARITIES: ItemRarity[] = ["COMMON", "UNCOMMON", "RARE", "VERY_RARE"];

/** Turns an enum label like "ALCHEMY_INGREDIENT" into readable text like "Alchemy Ingredient". */
export function formatEnumLabel(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

/** Readable item type, e.g. "Med Kit". */
export function formatItemType(type: ItemType): string {
  return formatEnumLabel(type);
}

/** Readable rarity, e.g. "Very Rare". */
export function formatItemRarity(rarity: ItemRarity): string {
  return formatEnumLabel(rarity);
}

/** Text colour per rarity, so rarity reads at a glance on the dark cards. */
export const RARITY_CLASSES: Record<ItemRarity, string> = {
  COMMON: "border-slate-500/60 bg-slate-700/40 text-slate-100",
  UNCOMMON: "border-emerald-500/60 bg-emerald-500/15 text-emerald-200",
  RARE: "border-sky-500/60 bg-sky-500/15 text-sky-200",
  VERY_RARE: "border-violet-500/60 bg-violet-500/20 text-violet-100",
};

/**
 * Formats a bronze amount in the book's denominations: 4500 -> "45 Gold", 50 -> "5 Silver", 5 -> "5 Bronze".
 * Gold is used for everything 1 Gold and up, the way the book lists prices.
 */
export function formatBronze(bronze: number): string {
  const trim = (value: number) => Number(value.toFixed(2)).toLocaleString();
  if (bronze >= 100) return `${trim(bronze / 100)} Gold`;
  if (bronze >= 10) return `${trim(bronze / 10)} Silver`;
  return `${bronze} Bronze`;
}

/** Formats an item's price, including the bundle size, e.g. "15 Gold for 10". */
export function formatItemCost(item: Item): string {
  if (item.costBronze === null) return "—";
  const cost = formatBronze(item.costBronze);
  return item.bundleQuantity > 1 ? `${cost} for ${item.bundleQuantity}` : cost;
}

/** Formats weapon damage, e.g. "1d8 + PD Slashing". */
export function formatWeaponDamage(item: Item): string | null {
  if (!item.weapon) return null;
  const { damageDice, damageBonus, damageType } = item.weapon;
  return `${damageDice}${damageBonus ? ` + ${damageBonus}` : ""} ${damageType.name}`;
}

/** Formats armor/shield modifiers, e.g. "+20% Dodge, -20% Stealth". */
export function formatModifiers(item: Item): string | null {
  const modifiers = item.details.modifiers;
  if (!modifiers || modifiers.length === 0) return null;
  return modifiers.map(({ skill, value }) => `${value > 0 ? "+" : ""}${value}% ${skill}`).join(", ");
}

/**
 * One short line summarising what matters most for the item's type, shown on its card
 * (e.g. damage for weapons, inventory slots for bags).
 */
export function formatItemSummary(item: Item): string | null {
  const details = item.details;
  switch (item.itemType) {
    case "WEAPON": {
      if (!item.weapon) return null;
      const range = item.weapon.rangeFt ? ` • ${item.weapon.rangeFt} ft` : "";
      return `${formatWeaponDamage(item)} • ${item.weapon.weaponType.name}${range}`;
    }
    case "ARMOR":
    case "SHIELD": {
      const parts = [
        details.strength_req ? `Str ${details.strength_req}` : null,
        formatModifiers(item),
      ].filter(Boolean);
      return parts.length > 0 ? parts.join(" • ") : null;
    }
    case "BAG":
      return details.inventory_slots ? `${details.inventory_slots} inventory slots` : null;
    case "SKILL_BOOK":
      return details.skills?.length ? `${details.skills.join(", ")} • +${details.skill_bonus ?? 0}%` : null;
    case "TOOL":
      return details.discipline ? `${formatEnumLabel(details.discipline)} tools` : null;
    case "ALCHEMY_INGREDIENT":
      return details.role ? `${formatEnumLabel(details.role)} ingredient` : null;
    case "AMMUNITION":
      return details.ammo_type ? `${formatEnumLabel(details.ammo_type)}s` : null;
    case "GEM":
      return details.magic_item_effect ?? null;
    case "METAL":
      return details.weapon_effect ? `Weapon: ${details.weapon_effect}` : null;
    default:
      return null;
  }
}
