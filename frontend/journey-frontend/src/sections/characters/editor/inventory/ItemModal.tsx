import { lazy, Suspense, useEffect, useState } from "react";
import { Info, Trash2 } from "lucide-react";
import { getItem } from "@/apis/characterApi";
import type { Item } from "@/models/Item";
import { formatItemCost, formatItemRarity, formatItemType, formatWeaponDamage } from "../../../compendium/components/items/itemFormatters";
import type { InventoryLocation } from "@/models/Character";
import { cn } from "@/lib/utils";
import type { DraftItem } from "../draft";
import { useDraft, useReadOnly, useUpdate } from "../hooks";
import { Modal, PrimaryButton, Stepper, TextField, inputClass } from "../ui";
import { PLACE_NAMES, attunedCount, attunementLimit, itemInSlot, itemLimit, itemName, placeIsFree } from "./inventoryHelpers";

const PLACES: InventoryLocation[] = ["ARMOR", "LEFT_HAND", "RIGHT_HAND", "BOTH_HANDS", "BAG", "COMPONENTS", "QUEST"];

const ItemDetailsModal = lazy(() =>
  import("../../../compendium/components/items/ItemDetailsModal").then((m) => ({ default: m.ItemDetailsModal })),
);

/** What the compendium says about the item: type, rarity, cost, text, and weapon stats. */
function ItemInfo({ item, onMore }: { item: Item; onMore: () => void }) {
  const damage = formatWeaponDamage(item);
  const facts = [
    formatItemType(item.itemType),
    formatItemRarity(item.rarity),
    item.isMagic ? "Magic" : null,
    item.requiresAttunement ? "Requires attunement" : null,
    item.costBronze !== null ? formatItemCost(item) : null,
  ].filter(Boolean);
  return (
    <div className="mb-3 space-y-2 rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm">
      <div className="flex flex-wrap gap-1.5">
        {facts.map((fact) => (
          <span key={fact} className="rounded-md bg-white px-1.5 py-0.5 text-xs font-medium text-slate-600 ring-1 ring-slate-200">{fact}</span>
        ))}
      </div>
      {damage && (
        <p className="text-slate-800">
          <span className="font-semibold">Damage</span> {damage}
          {item.weapon?.rangeFt ? ` · ${item.weapon.rangeFt} ft` : ""}
          {item.weapon?.properties.length ? ` · ${item.weapon.properties.map((p) => p.name).join(", ")}` : ""}
        </p>
      )}
      {item.description && <p className="whitespace-pre-line text-slate-700">{item.description}</p>}
      {item.effect && <p className="whitespace-pre-line text-slate-800"><span className="font-semibold">Effect</span> {item.effect}</p>}
      <button type="button" onClick={onMore} className="flex items-center gap-1 text-xs font-semibold text-violet-700">
        <Info size={13} /> Full details
      </button>
    </div>
  );
}

/** One stack: what it is, quantity, its own stack limit, charges, attunement, notes, where it is, or remove it. */
export function ItemModal({ itemKey, onClose }: { itemKey: string; onClose: () => void }) {
  const d = useDraft();
  const readOnly = useReadOnly();
  const update = useUpdate();
  const inv = d.inventory;
  const item = inv.items.find((i) => i.key === itemKey);
  const [ownLimit, setOwnLimit] = useState(item?.stackLimitOverride !== null && item?.stackLimitOverride !== undefined);
  const [compendium, setCompendium] = useState<Item | null>(null);
  const [showDetails, setShowDetails] = useState(false);
  const compendiumId = item?.itemId ?? null;
  useEffect(() => {
    if (!compendiumId) return;
    let cancelled = false;
    getItem(compendiumId).then((found) => !cancelled && setCompendium(found)).catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [compendiumId]);
  if (!item) return null;

  const change = (recipe: (i: DraftItem) => void) =>
    update((x) => {
      const target = x.inventory.items.find((i) => i.key === itemKey);
      if (target) recipe(target);
    });
  const limit = itemLimit(item, inv);
  const inSlot = item.location === "SLOT";
  const canAttune = item.attuned || attunedCount(inv) < attunementLimit(inv);

  const where = inSlot ? `slot:${item.slotKey}` : item.location;
  const targets = [
    ...PLACES.filter((p) => p === item.location || placeIsFree(inv, p, item.key)).map((p) => ({ value: p as string, label: PLACE_NAMES[p] })),
    ...inv.slots
      .filter((s) => s.key === item.slotKey || !itemInSlot(inv, s.key))
      .map((s) => ({ value: `slot:${s.key}`, label: `Slot · ${s.label || "Unnamed"}` })),
  ];

  return (
    <Modal
      title={itemName(item)}
      subtitle={[item.compendiumName && item.customName ? item.compendiumName : null, inSlot ? "In a slot" : PLACE_NAMES[item.location]].filter(Boolean).join(" · ")}
      onClose={onClose}
      footer={
        <div className="flex w-full items-center justify-between">
          {!readOnly ? (
            <button
              type="button"
              onClick={() => {
                update((x) => void (x.inventory.items = x.inventory.items.filter((i) => i.key !== itemKey)));
                onClose();
              }}
              className="flex items-center gap-1.5 rounded-lg px-2 py-2 text-sm font-semibold text-red-700 hover:bg-red-50"
            >
              <Trash2 size={15} /> Remove
            </button>
          ) : (
            <span />
          )}
          <PrimaryButton onClick={onClose}>Done</PrimaryButton>
        </div>
      }
    >
      {compendium && <ItemInfo item={compendium} onMore={() => setShowDetails(true)} />}
      {compendium && showDetails && (
        <Suspense fallback={null}>
          <ItemDetailsModal item={compendium} isOpen onClose={() => setShowDetails(false)} />
        </Suspense>
      )}
      <fieldset disabled={readOnly} className="space-y-3">
        <TextField
          label={item.itemId ? "Name (optional)" : "Name"}
          value={item.customName ?? ""}
          placeholder={item.compendiumName ?? ""}
          onChange={(v) => change((i) => void (i.customName = v.trim() ? v : item.itemId ? null : v))}
        />
        <div className="space-y-1">
          <label htmlFor="item-place" className="block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Where
          </label>
          <select
            id="item-place"
            value={where}
            onChange={(event) =>
              change((i) => {
                const value = event.target.value;
                if (value.startsWith("slot:")) {
                  i.location = "SLOT";
                  i.slotKey = value.slice(5);
                } else {
                  i.location = value as InventoryLocation;
                  i.slotKey = null;
                }
              })
            }
            className={inputClass}
          >
            {targets.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </div>

        <div className="divide-y divide-slate-100">
          <Stepper
            label="Quantity"
            hint={limit !== null ? `Stacks to ${limit} in a slot` : undefined}
            value={item.quantity}
            min={1}
            max={inSlot && limit !== null ? limit : undefined}
            onChange={(v) => change((i) => void (i.quantity = v))}
          />
          {item.maxCharges !== null && (
            <Stepper
              label="Charges"
              hint={`Out of ${item.maxCharges}`}
              value={item.chargesRemaining ?? 0}
              min={0}
              onChange={(v) => change((i) => void (i.chargesRemaining = v))}
            />
          )}
          <div className="py-2">
            <label className="flex items-center justify-between gap-3 text-sm font-medium text-slate-800">
              <span>
                Own stack limit
                <span className="block text-xs font-normal text-slate-500">
                  {item.baseStackLimit !== null ? `Normally ${item.baseStackLimit} × ${inv.stackLimitMultiplier}` : "A storyteller ruling or special container"}
                </span>
              </span>
              <input
                type="checkbox"
                checked={ownLimit}
                onChange={(event) => {
                  setOwnLimit(event.target.checked);
                  change((i) => void (i.stackLimitOverride = event.target.checked ? Math.max(i.quantity, limit ?? 1) : null));
                }}
                className="h-5 w-5 accent-violet-600"
              />
            </label>
            {ownLimit && item.stackLimitOverride !== null && (
              <Stepper label="Stacks to" value={item.stackLimitOverride} min={1} onChange={(v) => change((i) => void (i.stackLimitOverride = v))} />
            )}
          </div>
          {(item.requiresAttunement || item.attuned) && (
            <label className={cn("flex items-center justify-between gap-3 py-2 text-sm font-medium text-slate-800", !canAttune && "opacity-60")}>
              <span>
                Attuned
                <span className="block text-xs font-normal text-slate-500">
                  {attunedCount(inv)} of {attunementLimit(inv)} attunements used
                </span>
              </span>
              <input
                type="checkbox"
                checked={item.attuned}
                disabled={!canAttune}
                onChange={(event) => change((i) => void (i.attuned = event.target.checked))}
                className="h-5 w-5 accent-violet-600"
              />
            </label>
          )}
        </div>
        <TextField label="Notes" multiline value={item.notes} onChange={(v) => change((i) => void (i.notes = v))} />
      </fieldset>
    </Modal>
  );
}
