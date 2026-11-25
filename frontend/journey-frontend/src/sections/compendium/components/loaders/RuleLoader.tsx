import { useEffect, useState } from "react";
import { searchRules } from "@/apis/rulesApi";
import type { RuleFilterRequest } from "@/apis/rulesApi";
import type { RuleSearchResult } from "@/models/Rules";
import { Spinner } from "@/components/ui/spinner";
import { NoContent } from "../NoContent";
import { RuleList } from "../rules/RuleList";

/** The API's maximum number of results per search. */
const MAX_RESULTS = 50;

interface RuleLoaderProps {
  filters: RuleFilterRequest | null;
}

/**
 * Runs the rules search for the current filters. Rules are only listed for a search (there are over a
 * thousand sections), so with no search a prompt is shown instead. Results for an outdated search are discarded.
 */
export function RuleLoader({ filters }: RuleLoaderProps) {
  const [result, setResult] = useState<{
    filters: RuleFilterRequest;
    results: RuleSearchResult[];
    error: string | null;
  } | null>(null);

  /** Searches whenever the filters change. */
  useEffect(() => {
    if (!filters) return;
    const controller = new AbortController();
    searchRules(filters.query, filters.bookId ?? undefined, MAX_RESULTS, controller.signal)
      .then((results) => setResult({ filters, results, error: null }))
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setResult({ filters, results: [], error: err instanceof Error ? err.message : "Unable to search rules" });
      });
    return () => controller.abort();
  }, [filters]);

  if (!filters) {
    return (
      <NoContent
        message="Search the rules"
        hint="Type a word or phrase, e.g. prone, opportunity attack, or &quot;time points&quot;."
      />
    );
  }

  if (result?.filters !== filters) {
    return (
      <div className="flex min-h-[180px] items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-slate-600">
        <div className="flex items-center gap-2">
          <Spinner className="h-5 w-5" />
          <span>Searching rules...</span>
        </div>
      </div>
    );
  }

  if (result.error) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        Unable to search rules: {result.error}
      </div>
    );
  }

  return <RuleList key={JSON.stringify(filters)} results={result.results} truncated={result.results.length >= MAX_RESULTS} />;
}

export default RuleLoader;
