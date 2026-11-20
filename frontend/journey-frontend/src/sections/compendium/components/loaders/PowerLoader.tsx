import { useCallback, useEffect, useRef, useState } from "react";
import type { Power } from "../../../../models/Power";
import type { PowerPage } from "../../../../models/Power";
import type { PowerFilterRequest } from "@/apis/powerApi";
import { Spinner } from "@/components/ui/spinner";
import { PowerList } from "../powers/PowerList";
import { filterPowers, getPowers } from "../../../../apis/powerApi";

const API_PAGE_SIZE = 15;

interface PowerLoaderProps {
  filters: PowerFilterRequest | null;
}

export function PowerLoader({ filters }: PowerLoaderProps) {
  const [result, setResult] = useState<{
    filters: PowerFilterRequest | null;
    powers: Power[];
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

  useEffect(() => {
    currentFiltersRef.current = filters;
    let isMounted = true;

    async function loadPowers() {
      try {
        const page = filters
          ? await filterPowers(filters, 0, API_PAGE_SIZE)
          : await getPowers(0, API_PAGE_SIZE);

        if (isMounted) {
          setResult({
            filters,
            powers: page.content,
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
            powers: [],
            nextPage: 0,
            hasMore: false,
            error: err instanceof Error ? err.message : "Unable to load powers",
          });
        }
      }
    }

    void loadPowers();

    return () => {
      isMounted = false;
    };
  }, [filters]);

  const loadMore = useCallback((retry = false) => {
    const currentResult = result?.filters === filters ? result : null;
    if (!currentResult || loading || (loadMoreError && !retry) || !currentResult.hasMore || loadingMoreRef.current) {
      return;
    }

    loadingMoreRef.current = true;
    setLoadingMore(true);
    setLoadMoreError(null);

    const loadPage = filters
      ? filterPowers(filters, currentResult.nextPage, API_PAGE_SIZE)
      : getPowers(currentResult.nextPage, API_PAGE_SIZE);

    void loadPage
      .then((page: PowerPage) => {
        if (currentFiltersRef.current !== filters) {
          return;
        }

        setResult((previous) => {
          if (!previous || previous.filters !== filters) {
            return previous;
          }

          return {
            ...previous,
            powers: [...previous.powers, ...page.content],
            nextPage: currentResult.nextPage + 1,
            hasMore: page.number + 1 < page.totalPages,
          };
        });
        setLoadMoreError(null);
      })
      .catch((err: unknown) => {
        if (currentFiltersRef.current === filters) {
          setLoadMoreError(err instanceof Error ? err.message : "Unable to load more powers");
        }
      })
      .finally(() => {
        loadingMoreRef.current = false;
        setLoadingMore(false);
      });
  }, [filters, loadMoreError, loading, result]);
  const retryLoadMore = useCallback(() => loadMore(true), [loadMore]);

  if (loading) {
    return (
      <div className="flex min-h-[180px] items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-slate-600">
        <div className="flex items-center gap-2">
          <Spinner className="h-5 w-5" />
          <span>Loading powers...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        Unable to load powers: {error}
      </div>
    );
  }

  return (
    <PowerList
      key={filters ? JSON.stringify(filters) : "all-powers"}
      powers={result?.filters === filters ? result.powers : []}
      loadingMore={loadingMore}
      loadMoreError={loadMoreError}
      onEndReached={loadMore}
      onRetry={retryLoadMore}
    />
  );
}

export default PowerLoader;
