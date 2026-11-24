import { useCallback, useEffect, useRef, useState } from "react";
import type { Item, ItemPage } from "../../../../models/Item";
import type { ItemFilterRequest } from "@/apis/itemApi";
import { Spinner } from "@/components/ui/spinner";
import { ItemList } from "../items/ItemList";
import { filterItems, getItems } from "../../../../apis/itemApi";

const API_PAGE_SIZE = 15;

interface ItemLoaderProps {
  filters: ItemFilterRequest | null;
}

/**
 * Fetches items for the current filters (or all items when there are none) and handles paging.
 * Results from an outdated filter are discarded so fast filter changes never show stale data.
 */
export function ItemLoader({ filters }: ItemLoaderProps) {
  const [result, setResult] = useState<{
    filters: ItemFilterRequest | null;
    items: Item[];
    nextPage: number;
    hasMore: boolean;
    error: string | null;
  } | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
  const loadingMoreRef = useRef(false);
  const currentFiltersRef = useRef(filters);

  const loading = result === null || result.filters !== filters;
  const error = loading ? null : result?.error ?? null;

  /** Loads the first page whenever the filters change. */
  useEffect(() => {
    currentFiltersRef.current = filters;
    let isMounted = true;

    /** Requests page 0 and stores it, or stores the error message on failure. */
    async function loadItems() {
      try {
        const page = filters
          ? await filterItems(filters, 0, API_PAGE_SIZE)
          : await getItems(0, API_PAGE_SIZE);

        if (isMounted) {
          setResult({
            filters,
            items: page.content,
            nextPage: 1,
            hasMore: page.number + 1 < page.totalPages,
            error: null,
          });
          setLoadMoreError(null);
        }
      } catch (err) {
        if (isMounted) {
          setResult({
            filters,
            items: [],
            nextPage: 0,
            hasMore: false,
            error: err instanceof Error ? err.message : "Unable to load items",
          });
        }
      }
    }

    void loadItems();

    return () => {
      isMounted = false;
    };
  }, [filters]);

  /** Appends the next page; ignored while already loading, after an error (unless retrying), or at the end. */
  const loadMore = useCallback((retry = false) => {
    const currentResult = result?.filters === filters ? result : null;
    if (!currentResult || loading || (loadMoreError && !retry) || !currentResult.hasMore || loadingMoreRef.current) {
      return;
    }

    loadingMoreRef.current = true;
    setLoadingMore(true);
    setLoadMoreError(null);

    const loadPage = filters
      ? filterItems(filters, currentResult.nextPage, API_PAGE_SIZE)
      : getItems(currentResult.nextPage, API_PAGE_SIZE);

    void loadPage
      .then((page: ItemPage) => {
        if (currentFiltersRef.current !== filters) {
          return;
        }

        setResult((previous) => {
          if (!previous || previous.filters !== filters) {
            return previous;
          }

          return {
            ...previous,
            items: [...previous.items, ...page.content],
            nextPage: currentResult.nextPage + 1,
            hasMore: page.number + 1 < page.totalPages,
          };
        });
        setLoadMoreError(null);
      })
      .catch((err: unknown) => {
        if (currentFiltersRef.current === filters) {
          setLoadMoreError(err instanceof Error ? err.message : "Unable to load more items");
        }
      })
      .finally(() => {
        loadingMoreRef.current = false;
        setLoadingMore(false);
      });
  }, [filters, loadMoreError, loading, result]);

  /** Retries a failed "load more" request. */
  const retryLoadMore = useCallback(() => loadMore(true), [loadMore]);

  if (loading) {
    return (
      <div className="flex min-h-[180px] items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-slate-600">
        <div className="flex items-center gap-2">
          <Spinner className="h-5 w-5" />
          <span>Loading items...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        Unable to load items: {error}
      </div>
    );
  }

  return (
    <ItemList
      key={filters ? JSON.stringify(filters) : "all-items"}
      items={result?.filters === filters ? result.items : []}
      hasMore={result?.hasMore ?? false}
      hasFilters={filters !== null}
      loadingMore={loadingMore}
      loadMoreError={loadMoreError}
      onEndReached={loadMore}
      onRetry={retryLoadMore}
    />
  );
}

export default ItemLoader;
