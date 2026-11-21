import { useCallback, useRef, useState } from "react";
import type { UIEvent } from "react";
import { useVirtualizer } from "@tanstack/react-virtual";
import type { Power } from "../../../../models/Power";
import { Spinner } from "@/components/ui/spinner";
import { NoContent } from "../NoContent";
import { PowerCard } from "./PowerCard";
import { PowerDetailsModal } from "./PowerDetailsModal";

interface PowerListProps {
  powers: Power[];
  hasMore: boolean;
  hasFilters: boolean;
  loadingMore: boolean;
  loadMoreError: string | null;
  onEndReached: () => void;
  onRetry: () => void;
}

const OVERSCAN_COUNT = 5;
const PREFETCH_DISTANCE = 500;

/**
 * Virtualized, infinitely scrolling list of power cards.
 * Only visible cards are rendered, and the next page is requested shortly before the user reaches the end.
 */
export function PowerList({ powers, hasMore, hasFilters, loadingMore, loadMoreError, onEndReached, onRetry }: PowerListProps) {
  const scrollElementRef = useRef<HTMLDivElement>(null);
  const [selectedPower, setSelectedPower] = useState<Power | null>(null);
  /** Opens the details modal for the tapped power. */
  const openDetails = useCallback((power: Power) => setSelectedPower(power), []);
  /** Closes the details modal. */
  const closeDetails = useCallback(() => setSelectedPower(null), []);
  const virtualizer = useVirtualizer({
    count: powers.length,
    getScrollElement: () => scrollElementRef.current,
    estimateSize: () => 180,
    overscan: OVERSCAN_COUNT,
  });
  const virtualItems = virtualizer.getVirtualItems();
  /** Requests the next page once the user scrolls within PREFETCH_DISTANCE pixels of the bottom. */
  const handleScroll = useCallback((event: UIEvent<HTMLDivElement>) => {
    const element = event.currentTarget;
    if (element.scrollHeight - element.scrollTop - element.clientHeight <= PREFETCH_DISTANCE) {
      onEndReached();
    }
  }, [onEndReached]);

  if (powers.length === 0) {
    return (
      <NoContent
        message="No content to load"
        hint={hasFilters ? "No powers match your search or filters. Try clearing them." : undefined}
      />
    );
  }

  return (
    <>
      <div
        ref={scrollElementRef}
        className="max-h-[70vh] overflow-y-auto overscroll-contain pr-2"
        aria-label="Powers"
        onScroll={handleScroll}
      >
        <div className="relative w-full" style={{ height: `${virtualizer.getTotalSize()}px` }}>
          {virtualItems.map((virtualItem) => (
            <div
              key={virtualItem.key}
              data-index={virtualItem.index}
              ref={virtualizer.measureElement}
              className="absolute left-0 top-0 w-full pb-4"
              style={{ transform: `translateY(${virtualItem.start}px)` }}
            >
              <PowerCard power={powers[virtualItem.index]} onViewDetails={openDetails} />
            </div>
          ))}
        </div>

        {loadMoreError ? (
          <div className="flex items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            <span>Unable to load more powers: {loadMoreError}</span>
            <button
              type="button"
              onClick={onRetry}
              className="shrink-0 rounded-md border border-red-300 px-3 py-1.5 font-medium hover:bg-red-100"
            >
              Retry
            </button>
          </div>
        ) : loadingMore ? (
          <div className="flex items-center justify-center gap-2 py-3 text-sm text-slate-500">
            <Spinner className="h-4 w-4" />
            <span>Loading more powers...</span>
          </div>
        ) : !hasMore ? (
          <NoContent variant="footer" message="No more content to load" />
        ) : null}
      </div>

      {selectedPower && (
        <PowerDetailsModal power={selectedPower} isOpen onClose={closeDetails} />
      )}
    </>
  );
}

export default PowerList;
