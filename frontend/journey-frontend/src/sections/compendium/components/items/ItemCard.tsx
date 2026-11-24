import { memo } from "react";
import type { Item } from "../../../../models/Item";
import {
  RARITY_CLASSES,
  formatItemCost,
  formatItemRarity,
  formatItemSummary,
  formatItemType,
} from "./itemFormatters";

interface ItemCardProps {
  item: Item;
  onViewDetails: (item: Item) => void;
}

/**
 * Compact summary card for one item in the virtualized list.
 * The whole card is a button so it is an easy tap target on mobile; it opens the details modal.
 */
export const ItemCard = memo(function ItemCard({ item, onViewDetails }: ItemCardProps) {
  const summary = formatItemSummary(item);
  const blurb = item.description ?? item.effect;

  return (
    <button
      type="button"
      onClick={() => onViewDetails(item)}
      aria-label={`View details for ${item.name}`}
      className="block w-full rounded-xl border border-slate-700 bg-slate-900/80 p-4 text-left text-slate-100 shadow-sm transition-colors hover:border-slate-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-500 active:bg-slate-900"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate text-lg font-semibold text-white">{item.name}</h3>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-slate-300">
            <span className={`rounded-full border px-2 py-1 font-medium ${RARITY_CLASSES[item.rarity]}`}>
              {formatItemRarity(item.rarity)}
            </span>
            <span className="rounded-full bg-slate-800 px-2 py-1">{formatItemType(item.itemType)}</span>
            <span className="rounded-full bg-slate-800 px-2 py-1">{formatItemCost(item)}</span>
            {item.isMagic && (
              <span className="rounded-full border border-violet-500/40 bg-violet-500/10 px-2 py-1 text-violet-200">Magic</span>
            )}
            {item.requiresAttunement && (
              <span className="rounded-full border border-amber-500/40 bg-amber-500/10 px-2 py-1 text-amber-200">Attunement</span>
            )}
          </div>
        </div>

        <span
          aria-hidden="true"
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-slate-600 bg-slate-800 text-slate-200"
        >
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-4 w-4">
            <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
            <circle cx="12" cy="12" r="3.2" />
          </svg>
        </span>
      </div>

      {(summary || blurb) && (
        <div className="mt-4 space-y-2 text-sm text-slate-200">
          {summary && <div className="font-medium text-slate-200">{summary}</div>}
          {blurb && <p className="line-clamp-2 text-slate-300">{blurb}</p>}
        </div>
      )}
    </button>
  );
});

export default ItemCard;
