import { useCallback, useRef, useState } from "react";
import type { UIEvent } from "react";
import { useVirtualizer } from "@tanstack/react-virtual";
import type { Race } from "../../../../models/Race";
import { Spinner } from "@/components/ui/spinner";
import { NoContent } from "../NoContent";
import { RaceCard } from "./RaceCard";
import { RaceDetailsModal } from "./RaceDetailsModal";

interface RaceListProps {
  races: Race[];
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
 * Virtualized, infinitely scrolling list of race cards.
 * Only visible cards are rendered, and the next page is requested shortly before the user reaches the end.
 */
export function RaceList({ races, hasMore, hasFilters, loadingMore, loadMoreError, onEndReached, onRetry }: RaceListProps) {
  const scrollElementRef = useRef<HTMLDivElement>(null);
  const [selectedRace, setSelectedRace] = useState<Race | null>(null);

  /** Opens the details modal for the tapped race. */
  const openDetails = useCallback((race: Race) => setSelectedRace(race), []);

  /** Closes the details modal. */
  const closeDetails = useCallback(() => setSelectedRace(null), []);

  const virtualizer = useVirtualizer({
    count: races.length,
    getScrollElement: () => scrollElementRef.current,
    estimateSize: () => 200,
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

  if (races.length === 0) {
    return (
      <NoContent
        message="No content to load"
        hint={hasFilters ? "No races match your search or filters. Try clearing them." : undefined}
      />
    );
  }

  return (
    <>
      <div
        ref={scrollElementRef}
        className="max-h-[70vh] overflow-y-auto overscroll-contain pr-2"
        aria-label="Races"
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
              <RaceCard race={races[virtualItem.index]} onViewDetails={openDetails} />
            </div>
          ))}
        </div>

        {loadMoreError ? (
          <div className="flex items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            <span>Unable to load more races: {loadMoreError}</span>
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
            <span>Loading more races...</span>
          </div>
        ) : !hasMore ? (
          <NoContent variant="footer" message="No more content to load" />
        ) : null}
      </div>

      {selectedRace && (
        <RaceDetailsModal race={selectedRace} isOpen onClose={closeDetails} />
      )}
    </>
  );
}

export default RaceList;
