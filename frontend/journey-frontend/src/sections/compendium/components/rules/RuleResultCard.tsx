import { memo } from "react";
import type { RuleSearchResult } from "@/models/Rules";
import { HighlightedSnippet } from "../../../game-library/rules/HighlightedSnippet";

interface RuleResultCardProps {
  result: RuleSearchResult;
  onViewDetails: (result: RuleSearchResult) => void;
}

/** One rules search hit: chapter, section title, and the matching text with the search words highlighted. */
export const RuleResultCard = memo(function RuleResultCard({ result, onViewDetails }: RuleResultCardProps) {
  return (
    <button
      type="button"
      onClick={() => onViewDetails(result)}
      aria-label={`Read ${result.sectionTitle} in ${result.chapterTitle}`}
      className="block w-full rounded-xl border border-slate-200 bg-white p-4 text-left shadow-sm transition-colors hover:border-violet-300 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-500"
    >
      <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-violet-600">{result.chapterTitle}</p>
      <h3 className="mt-1 text-lg font-semibold text-slate-900">{result.sectionTitle}</h3>
      {result.snippet && (
        <p className="mt-2 line-clamp-3 text-sm leading-6 text-slate-600">
          <HighlightedSnippet text={result.snippet} />
        </p>
      )}
    </button>
  );
});

export default RuleResultCard;
