import { useCallback, useEffect, useRef, useState } from "react";
import type { Trait, TraitPage } from "../../../../models/Trait";
import type { TraitFilterRequest } from "@/apis/traitApi";
import { Spinner } from "@/components/ui/spinner";
import { TraitList } from "../traits/TraitList";
import { filterTraits, getTraits } from "../../../../apis/traitApi";

const API_PAGE_SIZE = 15;

interface TraitLoaderProps {
  filters: TraitFilterRequest | null;
}

/**
 * Fetches traits for the current filters (or all traits when there are none) and handles paging.
 * Results from an outdated filter are discarded so fast filter changes never show stale data.
 */
export function TraitLoader({ filters }: TraitLoaderProps) {
  const [result, setResult] = useState<{
    filters: TraitFilterRequest | null;
    traits: Trait[];
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
    async function loadTraits() {
      try {
        const page = filters
          ? await filterTraits(filters, 0, API_PAGE_SIZE)
          : await getTraits(0, API_PAGE_SIZE);

        if (isMounted) {
          setResult({
            filters,
            traits: page.content,
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
            traits: [],
            nextPage: 0,
            hasMore: false,
            error: err instanceof Error ? err.message : "Unable to load traits",
          });
        }
      }
    }

    void loadTraits();

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
      ? filterTraits(filters, currentResult.nextPage, API_PAGE_SIZE)
      : getTraits(currentResult.nextPage, API_PAGE_SIZE);

    void loadPage
      .then((page: TraitPage) => {
        if (currentFiltersRef.current !== filters) {
          return;
        }

        setResult((previous) => {
          if (!previous || previous.filters !== filters) {
            return previous;
          }

          return {
            ...previous,
            traits: [...previous.traits, ...page.content],
            nextPage: currentResult.nextPage + 1,
            hasMore: page.number + 1 < page.totalPages,
          };
        });
        setLoadMoreError(null);
      })
      .catch((err: unknown) => {
        if (currentFiltersRef.current === filters) {
          setLoadMoreError(err instanceof Error ? err.message : "Unable to load more traits");
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
          <span>Loading traits...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        Unable to load traits: {error}
      </div>
    );
  }

  return (
    <TraitList
      key={filters ? JSON.stringify(filters) : "all-traits"}
      traits={result?.filters === filters ? result.traits : []}
      hasMore={result?.hasMore ?? false}
      hasFilters={filters !== null}
      loadingMore={loadingMore}
      loadMoreError={loadMoreError}
      onEndReached={loadMore}
      onRetry={retryLoadMore}
    />
  );
}

export default TraitLoader;
