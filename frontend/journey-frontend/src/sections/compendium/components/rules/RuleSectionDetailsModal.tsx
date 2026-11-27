import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { BookOpen } from "lucide-react";
import { Link } from "react-router-dom";
import { getRuleChapter } from "@/apis/rulesApi";
import type { RuleChapter, RuleSearchResult, RuleSection } from "@/models/Rules";
import { Spinner } from "@/components/ui/spinner";
import { RuleEntityDetails } from "../../../game-library/rules/RuleEntityCard";
import { RuleMarkdown } from "../../../game-library/rules/RuleMarkdown";
import type { RuleEntitySelection } from "../../../game-library/rules/ruleLinks";

interface RuleSectionDetailsModalProps {
  result: RuleSearchResult;
  isOpen: boolean;
  onClose: () => void;
}

const HEADING_CLASSES: Record<number, string> = {
  0: "text-xl font-semibold text-slate-900",
  1: "text-lg font-semibold text-slate-900",
  2: "text-base font-semibold text-slate-900",
};

/** The section plus every section nested under it (they follow it in reading order until the next sibling). */
function sectionWithChildren(sections: RuleSection[], sectionId: string): RuleSection[] {
  const start = sections.findIndex((section) => section.id === sectionId);
  if (start < 0) return [];
  const root = sections[start];
  let end = start + 1;
  while (end < sections.length && sections[end].level > root.level) end++;
  return sections.slice(start, end);
}

/**
 * Reads one rules section from the search results. It slides up as a bottom sheet on phones and is a centered
 * dialog on larger screens; entity cards inside it open their own details, and "Open in book" jumps to the
 * section in the reader.
 */
export function RuleSectionDetailsModal({ result, isOpen, onClose }: RuleSectionDetailsModalProps) {
  const [loaded, setLoaded] = useState<{ chapterId: string; chapter: RuleChapter | null; error: string | null } | null>(null);
  const [selection, setSelection] = useState<RuleEntitySelection | null>(null);

  /** Loads the section's chapter (it carries the entities and images the section links to). */
  useEffect(() => {
    if (!isOpen) return;
    const controller = new AbortController();
    getRuleChapter(result.chapterId, controller.signal)
      .then((chapter) => setLoaded({ chapterId: result.chapterId, chapter, error: null }))
      .catch((reason: unknown) => {
        if (controller.signal.aborted) return;
        setLoaded({ chapterId: result.chapterId, chapter: null, error: reason instanceof Error ? reason.message : "Unable to load section" });
      });
    return () => controller.abort();
  }, [isOpen, result.chapterId]);

  /** Closes on Escape (unless an entity's details are open on top) and locks background scrolling. */
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !selection) onClose();
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, onClose, selection]);

  const current = loaded?.chapterId === result.chapterId ? loaded : null;
  const sections = useMemo(
    () => (current?.chapter ? sectionWithChildren(current.chapter.sections, result.sectionId) : []),
    [current, result.sectionId],
  );

  if (!isOpen) return null;

  const readerLink = result.bookId
    ? `/library/books/${result.bookId}/chapters/${result.chapterId}#${result.slug}`
    : null;

  // Portaled to <body> so ancestors with backdrop-filter/transform cannot trap the fixed overlay.
  // The entity details sit outside the overlay so their backdrop clicks do not bubble to it and close this too.
  return (
    <>
      {createPortal(
    <div
      className="fixed inset-0 z-[70] flex items-end justify-center bg-slate-950/80 backdrop-blur-sm sm:items-center sm:p-4"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="rule-section-title"
        className="flex h-[85dvh] w-full max-w-2xl flex-col rounded-t-2xl border border-slate-200 bg-white text-slate-900 shadow-2xl sm:h-auto sm:max-h-[90dvh] sm:rounded-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="shrink-0 border-b border-slate-100 p-5 pb-4">
          <div className="mx-auto mb-3 h-1.5 w-10 rounded-full bg-slate-300 sm:hidden" aria-hidden="true" />
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <p className="text-xs uppercase tracking-[0.2em] text-violet-600">{result.chapterTitle}</p>
              <h2 id="rule-section-title" className="mt-1 text-2xl font-bold text-slate-900">{result.sectionTitle}</h2>
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label="Close rule section"
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-600 hover:bg-slate-100"
              title="Close"
            >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="h-4 w-4">
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </div>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-3">
          {!current && (
            <div className="flex min-h-[160px] items-center justify-center gap-2 text-slate-600">
              <Spinner className="h-5 w-5" /> <span>Loading section...</span>
            </div>
          )}
          {current?.error && (
            <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
              Unable to load section: {current.error}
            </div>
          )}
          {current?.chapter && sections.length === 0 && (
            <p className="py-6 text-sm text-slate-500">This section is no longer in the chapter.</p>
          )}
          {current?.chapter &&
            sections.map((section, index) => {
              const depth = Math.min(section.level - sections[0].level, 2);
              return (
                <section key={section.id}>
                  {/* The first section's title is already the dialog title. */}
                  {index > 0 && <h3 className={`${HEADING_CLASSES[depth]} mb-2 mt-6`}>{section.title}</h3>}
                  {section.body ? (
                    <RuleMarkdown body={section.body} chapter={current.chapter!} onOpenEntity={setSelection} />
                  ) : index === 0 && sections.length === 1 ? (
                    <p className="py-2 text-sm text-slate-500">This heading has no text of its own.</p>
                  ) : null}
                </section>
              );
            })}
        </div>

        {readerLink && (
          <div className="flex shrink-0 justify-end border-t border-slate-100 p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
            <Link
              to={readerLink}
              className="flex items-center gap-2 rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700"
            >
              <BookOpen size={16} /> Open in book
            </Link>
          </div>
        )}
      </div>
    </div>,
    document.body,
      )}
      <RuleEntityDetails selection={selection} onClose={() => setSelection(null)} />
    </>
  );
}

export default RuleSectionDetailsModal;
