import { useEffect, useState } from "react";
import { getItemReferenceData } from "@/apis/itemApi";
import type { Item, ItemRarity, ItemReferenceData, ItemType } from "@/models/Item";
import { ITEM_RARITIES, ITEM_TYPES, formatItemRarity, formatItemType } from "../../compendium/components/items/itemFormatters";
import type { FormProps } from "./FormKit";
import { Area, Check, Chips, ContentFormModal, Num, Row, Select, Text } from "./FormKit";
import { orNull } from "./orNull";

/** Create or edit one of the campaign's items; weapons also get their weapon stats. */
export default function ItemForm({ campaignId, initial, onDone, onClose }: FormProps<Item>) {
  const [reference, setReference] = useState<ItemReferenceData | null>(null);
  const [name, setName] = useState(initial?.name ?? "");
  const [type, setType] = useState<ItemType | "">(initial?.itemType ?? "MISCELLANEOUS");
  const [rarity, setRarity] = useState<ItemRarity | "">(initial?.rarity ?? "COMMON");
  const [magic, setMagic] = useState(initial?.isMagic ?? false);
  const [attunement, setAttunement] = useState(initial?.requiresAttunement ?? false);
  const [description, setDescription] = useState(initial?.description ?? "");
  const [effect, setEffect] = useState(initial?.effect ?? "");
  const [cost, setCost] = useState<number | null>(initial?.costBronze ?? null);
  const [stack, setStack] = useState<number | null>(initial?.stackLimit ?? 1);
  const [bundle, setBundle] = useState<number | null>(initial?.bundleQuantity ?? 1);
  const [charges, setCharges] = useState<number | null>(initial?.charges ?? null);
  const w = initial?.weapon;
  const [weaponType, setWeaponType] = useState(w?.weaponType.name ?? "");
  const [dice, setDice] = useState(w?.damageDice ?? "1d6");
  const [bonus, setBonus] = useState(w?.damageBonus ?? "");
  const [damageType, setDamageType] = useState(w?.damageType.name ?? "");
  const [rangeFt, setRangeFt] = useState<number | null>(w?.rangeFt ?? null);
  const [mag, setMag] = useState<number | null>(w?.mag ?? null);
  const [properties, setProperties] = useState<string[]>(w?.properties.map((p) => p.name) ?? []);

  useEffect(() => {
    getItemReferenceData().then(setReference).catch(() => undefined);
  }, []);

  const weapon = type === "WEAPON";

  return (
    <ContentFormModal
      kind="items"
      singular="item"
      campaignId={campaignId}
      editingId={initial?.id ?? null}
      name={name}
      onDone={onDone}
      onClose={onClose}
      build={() => {
        if (!name.trim() || !type || !rarity) return "An item needs a name, a type, and a rarity";
        if (weapon && (!weaponType || !damageType || !/^\d+d\d+$/i.test(dice.trim()))) {
          return "A weapon needs a weapon type, damage dice like 1d8, and a damage type";
        }
        return {
          name: name.trim(), item_type: type, rarity, is_magic: magic, description: orNull(description), effect: orNull(effect),
          cost_bronze: cost, stack_limit: stack ?? 1, bundle_quantity: bundle ?? 1, requires_attunement: attunement, charges,
          // Keep what this form does not edit (type-specific details, base item, material).
          details: initial?.details ?? undefined,
          base_item_id: initial?.baseItem?.id ?? null,
          material_id: initial?.material?.id ?? null,
          weapon: weapon
            ? { weapon_type: weaponType, damage_dice: dice.trim(), damage_bonus: orNull(bonus), damage_type: damageType, range_ft: rangeFt, mag }
            : null,
          weapon_properties: weapon ? properties : [],
        };
      }}
    >
      <Text label="Name" value={name} onChange={setName} />
      <Row>
        <Select label="Type" value={type} options={ITEM_TYPES.map((t) => ({ value: t, label: formatItemType(t) }))} onChange={setType} />
        <Select label="Rarity" value={rarity} options={ITEM_RARITIES.map((r) => ({ value: r, label: formatItemRarity(r) }))} onChange={setRarity} />
        <Num label="Cost (bronze)" value={cost} onChange={setCost} />
        <Num label="Charges" value={charges} onChange={setCharges} />
      </Row>
      <Row>
        <Num label="Stacks to" value={stack} onChange={setStack} min={1} />
        <Num label="Sold in bundles of" value={bundle} onChange={setBundle} min={1} />
      </Row>
      <div className="flex flex-wrap gap-x-4 gap-y-2">
        <Check label="Magic" checked={magic} onChange={setMagic} />
        <Check label="Requires attunement" checked={attunement} onChange={setAttunement} />
      </div>
      <Area label="Description" value={description} onChange={setDescription} rows={2} />
      <Area label="Effect" value={effect} onChange={setEffect} rows={3} />
      {weapon && (
        <div className="space-y-3 rounded-xl border border-slate-200 p-3">
          <h4 className="text-xs font-bold uppercase tracking-wide text-violet-700">Weapon</h4>
          <Row>
            <Select label="Weapon type" value={weaponType} allowEmpty="Choose…" options={(reference?.weaponTypes ?? []).map((t) => ({ value: t.name, label: t.name }))} onChange={setWeaponType} />
            <Text label="Damage dice" value={dice} onChange={setDice} placeholder="1d8" />
            <Text label="Damage bonus" value={bonus} onChange={setBonus} placeholder="PD" />
            <Select label="Damage type" value={damageType} allowEmpty="Choose…" options={(reference?.damageTypes ?? []).map((t) => ({ value: t.name, label: t.name }))} onChange={setDamageType} />
          </Row>
          <Row>
            <Num label="Range (ft)" value={rangeFt} onChange={setRangeFt} />
            <Num label="MAG" value={mag} onChange={setMag} min={1} />
          </Row>
          <Chips label="Properties" values={properties} options={(reference?.weaponProperties ?? []).map((p) => ({ value: p.name, label: p.name }))} onChange={setProperties} />
        </div>
      )}
    </ContentFormModal>
  );
}
