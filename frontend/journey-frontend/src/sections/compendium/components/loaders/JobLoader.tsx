import { useCallback, useEffect, useRef, useState } from "react";
import type { Job, JobPage } from "../../../../models/Job";
import type { JobFilterRequest } from "@/apis/jobApi";
import { Spinner } from "@/components/ui/spinner";
import { JobList } from "../jobs/JobList";
import { filterJobs, getJobs } from "../../../../apis/jobApi";

const API_PAGE_SIZE = 15;

interface JobLoaderProps {
  filters: JobFilterRequest | null;
}

/**
 * Fetches jobs for the current filters (or all jobs when there are none) and handles paging.
 * Results from an outdated filter are discarded so fast filter changes never show stale data.
 */
export function JobLoader({ filters }: JobLoaderProps) {
  const [result, setResult] = useState<{
    filters: JobFilterRequest | null;
    jobs: Job[];
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
    async function loadJobs() {
      try {
        const page = filters
          ? await filterJobs(filters, 0, API_PAGE_SIZE)
          : await getJobs(0, API_PAGE_SIZE);

        if (isMounted) {
          setResult({
            filters,
            jobs: page.content,
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
            jobs: [],
            nextPage: 0,
            hasMore: false,
            error: err instanceof Error ? err.message : "Unable to load jobs",
          });
        }
      }
    }

    void loadJobs();

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
      ? filterJobs(filters, currentResult.nextPage, API_PAGE_SIZE)
      : getJobs(currentResult.nextPage, API_PAGE_SIZE);

    void loadPage
      .then((page: JobPage) => {
        if (currentFiltersRef.current !== filters) {
          return;
        }

        setResult((previous) => {
          if (!previous || previous.filters !== filters) {
            return previous;
          }

          return {
            ...previous,
            jobs: [...previous.jobs, ...page.content],
            nextPage: currentResult.nextPage + 1,
            hasMore: page.number + 1 < page.totalPages,
          };
        });
        setLoadMoreError(null);
      })
      .catch((err: unknown) => {
        if (currentFiltersRef.current === filters) {
          setLoadMoreError(err instanceof Error ? err.message : "Unable to load more jobs");
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
          <span>Loading jobs...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        Unable to load jobs: {error}
      </div>
    );
  }

  return (
    <JobList
      key={filters ? JSON.stringify(filters) : "all-jobs"}
      jobs={result?.filters === filters ? result.jobs : []}
      hasMore={result?.hasMore ?? false}
      hasFilters={filters !== null}
      loadingMore={loadingMore}
      loadMoreError={loadMoreError}
      onEndReached={loadMore}
      onRetry={retryLoadMore}
    />
  );
}

export default JobLoader;
