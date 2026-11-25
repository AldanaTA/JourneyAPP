import { Link } from "react-router-dom";
import type { RuleBook, RuleSection } from "../../../models/Rules";
import { sectionAnchorId } from "./ruleLinks";

interface RuleContentsProps {
  book: RuleBook;
  currentChapterId: string | null;
  /** Headings of the open chapter, shown under it. */
  headings: RuleSection[];
  /** Called after any link is followed (closes the mobile sheet). */
  onNavigate?: () => void;
}

/** Table of contents: the book's chapters, with the open chapter's main headings nested under it. */
export function RuleContents({ book, currentChapterId, headings, onNavigate }: RuleContentsProps) {
  const baseLevel = Math.min(...headings.map((section) => section.level));

  return (
    <nav aria-label={`${book.title} contents`}>
      <ol className="space-y-1">
        {book.chapters.map((chapter) => {
          const current = chapter.id === currentChapterId;
          return (
            <li key={chapter.id}>
              <Link
                to={`/library/books/${book.id}/chapters/${chapter.id}`}
                onClick={onNavigate}
                aria-current={current ? "page" : undefined}
                className={`flex gap-2 rounded-lg px-2 py-1.5 text-sm transition-colors ${
                  current ? "bg-violet-100 font-semibold text-violet-800" : "text-slate-700 hover:bg-slate-100"
                }`}
              >
                <span className="w-5 shrink-0 text-right tabular-nums text-slate-400">{chapter.sortOrder}</span>
                <span className="min-w-0">{chapter.title}</span>
              </Link>

              {current && headings.length > 0 && (
                <ol className="mb-2 ml-7 mt-1 space-y-0.5 border-l border-violet-200 pl-2">
                  {headings.map((section) => (
                    <li key={section.id}>
                      <a
                        href={`#${section.slug}`}
                        onClick={(event) => {
                          event.preventDefault();
                          document.getElementById(sectionAnchorId(section.slug))?.scrollIntoView({ behavior: "smooth", block: "start" });
                          window.history.replaceState(null, "", `#${section.slug}`);
                          onNavigate?.();
                        }}
                        className={`block truncate rounded px-1.5 py-0.5 text-xs text-slate-600 hover:bg-slate-100 hover:text-slate-900 ${
                          section.level > baseLevel ? "pl-4" : ""
                        }`}
                      >
                        {section.title}
                      </a>
                    </li>
                  ))}
                </ol>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
