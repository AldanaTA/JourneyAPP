import { useState } from "react";
import { filterItems } from "@/apis/itemApi";
import type { Item, ItemType } from "@/models/Item";
import { ITEM_TYPES, formatItemType } from "@/sections/compendium/components/items/itemFormatters";
import { useSearchSourceIds } from "../referenceStore";
import { inputClass } from "../ui";
import { FilterSelect, PickerModal } from "./PickerModal";

/**
 * Pick one compendium item, or name a custom one ("A sealed letter"). {@code types} preselects the item types
 * that fit the place (armor for the armor place, bags for the bag place).
 */
export default function ItemPicker({
  title,
  types,
  allowCustom = true,
  onPick,
  onClose,
}: {
  title: string;
  types?: ItemType[];
  allowCustom?: boolean;
  onPick: (item: Item | null, customName?: string) => void;
  onClose: () => void;
}) {
  const sourceIds = useSearchSourceIds();
  const [type, setType] = useState<ItemType | "">(types?.length === 1 ? types[0] : "");
  const [custom, setCustom] = useState("");
  const typeFilter = type ? [type] : (types ?? []);
  return (
    <PickerModal<Item>
      title={title}
      searchPlaceholder="Search items"
      filters={
        <>
          <FilterSelect
            label={types ? "Fitting types" : "Any type"}
            options={(types ?? ITEM_TYPES).map((t) => ({ value: t, label: formatItemType(t) }))}
            value={type}
            onChange={setType}
          />
          {allowCustom && (
            <form
              className="flex flex-1 gap-1.5"
              onSubmit={(event) => {
                event.preventDefault();
                if (custom.trim()) {
                  onPick(null, custom.trim());
                  onClose();
                }
              }}
            >
              <input
                value={custom}
                onChange={(event) => setCustom(event.target.value)}
                placeholder="…or a custom item"
                aria-label="Custom item name"
                className={`${inputClass} min-w-0 py-1.5`}
              />
              <button type="submit" disabled={!custom.trim()} className="rounded-lg border border-slate-200 px-2.5 text-sm font-medium text-slate-700 disabled:opacity-40">
                Add
              </button>
            </form>
          )}
        </>
      }
      filterKey={`${typeFilter.join(",")}|${sourceIds.join(",")}`}
      load={(page, size, search) =>
        filterItems(
          { itemTypes: typeFilter, rarities: [], magicOnly: false, attunementOnly: false, weaponTypes: [], damageTypes: [], page, size, name: search || null, sourceIds },
          page,
          size,
        )
      }
      getId={(i) => i.id}
      renderRow={(i) => (
        <>
          <span className="flex items-baseline justify-between gap-2">
            <span className="font-semibold text-slate-900">{i.name}</span>
            <span className="shrink-0 text-xs text-slate-500">{formatItemType(i.itemType)}</span>
          </span>
          <span className="block text-xs text-slate-500">
            Stacks to {i.stackLimit}
            {i.requiresAttunement ? " · Attunement" : ""}
            {i.charges ? ` · ${i.charges} charges` : ""}
          </span>
        </>
      )}
      renderDetails={(i) => (i.effect || i.description ? <span className="block text-sm text-slate-600">{i.effect ?? i.description}</span> : null)}
      confirmLabel={() => "Add item"}
      onConfirm={([item]) => {
        if (item) onPick(item);
        onClose();
      }}
      onClose={onClose}
    />
  );
}
