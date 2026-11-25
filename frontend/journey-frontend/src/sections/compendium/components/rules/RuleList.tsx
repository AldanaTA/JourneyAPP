import { lazy, Suspense, useCallback, useState } from "react";
import type { RuleSearchResult } from "@/models/Rules";
import { NoContent } from "../NoContent";
import { RuleResultCard } from "./RuleResultCard";

// The section sheet renders Markdown, so it (and the Markdown renderer) is only downloaded when a result is opened.
const RuleSectionDetailsModal = lazy(() => import("./RuleSectionDetailsModal"));

interface RuleListProps {
  results: RuleSearchResult[];
  /** True when the API returned its maximum, so there may be more matches than shown. */
  truncated: boolean;
}

/** Rules search results, best match first; tapping one opens the section. */
export function RuleList({ results, truncated }: RuleListProps) {
  const [selected, setSelected] = useState<RuleSearchResult | null>(null);

  const openDetails = useCallback((result: RuleSearchResult) => setSelected(result), []);
  const closeDetails = useCallback(() => setSelected(null), []);

  if (results.length === 0) {
    return <NoContent message="No rules match your search" hint="Try fewer or different words." />;
  }

  return (
    <>
      <div className="max-h-[70vh] space-y-4 overflow-y-auto overscroll-contain pr-2" aria-label="Rules search results">
        {results.map((result) => (
          <RuleResultCard key={result.sectionId} result={result} onViewDetails={openDetails} />
        ))}
        <NoContent
          variant="footer"
          message={truncated ? `Top ${results.length} matches — refine your search for more` : "End of results"}
        />
      </div>

      {selected && (
        <Suspense fallback={null}>
          <RuleSectionDetailsModal result={selected} isOpen onClose={closeDetails} />
        </Suspense>
      )}
    </>
  );
}

export default RuleList;
