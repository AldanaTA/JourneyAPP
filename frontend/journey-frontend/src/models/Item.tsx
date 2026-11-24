/** What an item is; mirrors the backend ItemType enum. Whether it is magic is the separate isMagic flag. */
export type ItemType =
  | "WEAPON"
  | "ARMOR"
  | "SHIELD"
  | "AMMUNITION"
  | "POTION"
  | "ACCESSORY"
  | "MAGIC_ITEM"
  | "BAG"
  | "MED_KIT"
  | "SKILL_BOOK"
  | "TOOL"
  | "ALCHEMY_INGREDIENT"
  | "COOKING_INGREDIENT"
  | "GEM"
  | "METAL"
  | "MISCELLANEOUS";

/** Item rarity, lowest to highest; mirrors the backend ItemRarity enum. */
export type ItemRarity = "COMMON" | "UNCOMMON" | "RARE" | "VERY_RARE";

/** A weapon type and the effect every weapon of that type grants (e.g. Bow -> Nimble Shot). */
export interface WeaponTypeInfo {
  name: string;
  effectName: string;
  effect: string;
}

/** A weapon property and its rules text. */
export interface WeaponPropertyInfo {
  name: string;
  description: string;
}

/** A damage type, its effect, and the skills that defend against it. */
export interface DamageTypeInfo {
  name: string;
  effect: string;
  defenses: string[];
}

/** Resolved weapon stats (own or inherited from the base item). */
export interface ItemWeapon {
  weaponType: WeaponTypeInfo;
  damageDice: string;
  damageBonus: string | null;
  damageType: DamageTypeInfo;
  rangeFt: number | null;
  mag: number | null;
  properties: WeaponPropertyInfo[];
}

/** The metal an item is made of, with the bonuses it grants. */
export interface ItemMaterial {
  id: string;
  name: string;
  armorEffect: string | null;
  weaponEffect: string | null;
}

/**
 * Type-specific fields, merged with the base item's. Which keys exist depends on itemType
 * (e.g. strength_req/modifiers for armor, inventory_slots for bags, skills/sessions for skill books).
 */
export interface ItemDetails {
  strength_req?: number;
  modifiers?: Array<{ skill: string; value: number }>;
  ammo_type?: "arrow" | "bullet";
  inventory_slots?: number;
  skills?: string[];
  skill_bonus?: number;
  sessions?: number;
  discipline?: "alchemy" | "enchanting" | "smithing";
  role?: "tonic" | "resistance" | "poison" | "enhancer";
  magic_item_effect?: string;
  armor_effect?: string;
  weapon_effect?: string;
  [key: string]: unknown;
}

/** A single item as returned by the /api/v1/items endpoints, with inheritance already resolved. */
export interface Item {
  id: string;
  name: string;
  itemType: ItemType;
  rarity: ItemRarity;
  isMagic: boolean;
  description: string | null;
  effect: string | null;
  /** Price in bronze; null when the item has no listed price. */
  costBronze: number | null;
  /** Units that fit in one inventory slot. */
  stackLimit: number;
  /** Units bought for costBronze (e.g. 10 arrows). */
  bundleQuantity: number;
  requiresAttunement: boolean;
  /** Null for passive items / items without charges. */
  charges: number | null;
  details: ItemDetails;
  material: ItemMaterial | null;
  baseItem: { id: string; name: string } | null;
  weapon: ItemWeapon | null;
  contentSourceName: string | null;
}

/** A Spring Data page of items. */
export interface ItemPage {
  content: Item[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** Weapon reference data, used for filter options. */
export interface ItemReferenceData {
  weaponTypes: WeaponTypeInfo[];
  weaponProperties: WeaponPropertyInfo[];
  damageTypes: DamageTypeInfo[];
}
