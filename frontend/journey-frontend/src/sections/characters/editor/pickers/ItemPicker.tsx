import { lazy, Suspense, useState } from "react";
import { filterItems } from "@/apis/itemApi";
import type { Item, ItemType } from "@/models/Item";
import { formatItemType } from "@/sections/compendium/components/items/itemFormatters";
import { useSearchSourceIds } from "../referenceStore";
import { inputClass } from "../ui";
import { PickerModal } from "./PickerModal";
import type { ItemFilterRequest } from "@/apis/itemApi";
import { ItemFilter } from "@/sections/compendium/components/filters/ItemFilter";

const NO_ITEM_FILTERS: ItemFilterRequest = {
  itemTypes: [], rarities: [], magicOnly: false, attunementOnly: false, weaponTypes: [], damageTypes: [], page: 0, size: 20, name: null,
};

const ItemDetailsModal = lazy(() =>
  import("@/sections/compendium/components/items/ItemDetailsModal").then((m) => ({ default: m.ItemDetailsModal })),
);

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
  const [request, setRequest] = useState<ItemFilterRequest | null>(null);
  const [custom, setCustom] = useState("");
  return (
    <PickerModal<Item>
      title={title}
      searchPlaceholder="Search items"
      hideSearch
      filters={
        <div className="w-full space-y-2">
          <ItemFilter onApply={setRequest} initialFilters={request} />
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
        </div>
      }
      filterKey={`${JSON.stringify(request)}|${(types ?? []).join(",")}|${sourceIds.join(",")}`}
      load={(page, size) => {
        const base = request ?? NO_ITEM_FILTERS;
        // Without a type filter, the place's own types (armor for the armor place, ...).
        const itemTypes = base.itemTypes.length ? base.itemTypes : (types ?? []);
        return filterItems({ ...base, itemTypes, sourceIds, page, size }, page, size);
      }}
      getId={(i) => i.id}
      renderFullDetails={(i, close) => (
        <Suspense fallback={null}>
          <ItemDetailsModal item={i} isOpen onClose={close} />
        </Suspense>
      )}
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
