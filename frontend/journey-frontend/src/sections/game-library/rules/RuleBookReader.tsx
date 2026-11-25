import { useCallback, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import { ArrowLeft, ChevronLeft, ChevronRight, ListTree } from "lucide-react";
import { Link, Navigate, useLocation, useNavigate, useParams } from "react-router-dom";
import { getRuleBook, getRuleChapter } from "../../../apis/rulesApi";
import type { RuleBook, RuleChapter, RuleSearchResult, RuleSection } from "../../../models/Rules";
import { Spinner } from "@/components/ui/spinner";
import { NoContent } from "../../compendium/components/NoContent";
import { RuleContents } from "./RuleContents";
import { RuleEntityDetails } from "./RuleEntityCard";
import { RuleMarkdown } from "./RuleMarkdown";
import { RuleSearch } from "./RuleSearch";
import { sectionAnchorId } from "./ruleLinks";
import type { RuleEntitySelection } from "./ruleLinks";

type LoadState<T> = { status: "loading" } | { status: "error"; message: string } | { status: "ready"; data: T };

/** Heading styles by section level (1 = biggest). */
const HEADING_CLASSES: Record<number, string> = {
  1: "text-2xl font-semibold tracking-tight text-slate-900 md:text-3xl",
  2: "text-xl font-semibold tracking-tight text-slate-900",
  3: "text-lg font-semibold text-slate-900",
  4: "text-base font-semibold text-slate-900",
  5: "text-sm font-bold uppercase tracking-wide text-slate-700",
  6: "text-sm font-semibold text-slate-600",
};

/** Number of heading levels (from the top level down) listed in the table of contents. */
const CONTENTS_DEPTH = 2;

/** Loads a value with an async function, re-running when the key changes; aborts stale requests. */
function useLoad<T>(key: string | null, load: (key: string, signal: AbortSignal) => Promise<T>, attempt: number) {
  const [state, setState] = useState<LoadState<T> & { key: string | null }>({ status: "loading", key });

  useEffect(() => {
    if (!key) return;
    const controller = new AbortController();
    load(key, controller.signal)
      .then((data) => setState({ status: "ready", data, key }))
      .catch((reason: unknown) => {
        if (controller.signal.aborted) return;
        setState({ status: "error", message: reason instanceof Error ? reason.message : "Could not load", key });
      });
    return () => controller.abort();
  }, [key, load, attempt]);

  // A result for a previous key counts as loading, so the old chapter never flashes under a new title.
  return state.key === key ? state : ({ status: "loading", key } as const);
}

/**
 * Reader for one rulebook: table of contents, search, and the current chapter rendered from Markdown with
 * inline compendium cards. Routes: /library/books/:bookId and /library/books/:bookId/chapters/:chapterId,
 * with an optional #section-slug to jump to a heading.
 */
function RuleBookReader() {
  const { bookId = "", chapterId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [attempt, setAttempt] = useState(0);
  const [selection, setSelection] = useState<RuleEntitySelection | null>(null);
  const [contentsOpen, setContentsOpen] = useState(false);

  const book = useLoad<RuleBook>(bookId, getRuleBook, attempt);
  const chapter = useLoad<RuleChapter>(chapterId ?? null, getRuleChapter, attempt);

  const retry = useCallback(() => setAttempt((value) => value + 1), []);
  const closeDetails = useCallback(() => setSelection(null), []);

  const headings = useMemo<RuleSection[]>(() => {
    if (chapter.status !== "ready") return [];
    const sections = chapter.data.sections;
    if (sections.length === 0) return [];
    const top = Math.min(...sections.map((section) => section.level));
    return sections.filter((section) => section.level < top + CONTENTS_DEPTH);
  }, [chapter]);

  /** Jumps to the #slug heading once the chapter has rendered, or to the top for a new chapter. */
  useEffect(() => {
    if (chapter.status !== "ready") return;
    const slug = decodeURIComponent(location.hash.replace(/^#/, ""));
    const target = slug ? document.getElementById(sectionAnchorId(slug)) : null;
    if (target) {
      target.scrollIntoView({ block: "start" });
    } else {
      window.scrollTo({ top: 0 });
    }
  }, [chapter, location.hash]);

  const openSearchResult = useCallback(
    (result: RuleSearchResult) => {
      navigate(`/library/books/${result.bookId ?? bookId}/chapters/${result.chapterId}#${result.slug}`);
    },
    [navigate, bookId],
  );

  if (book.status === "loading") {
    return <LoadingPanel message="Loading book…" />;
  }
  if (book.status === "error") {
    return <ErrorPanel message={book.message} onRetry={retry} />;
  }

  const chapters = book.data.chapters;
  if (!chapterId) {
    return chapters.length > 0 ? (
      <Navigate to={`/library/books/${bookId}/chapters/${chapters[0].id}`} replace />
    ) : (
      <div className="space-y-5">
        <BackToLibrary />
        <NoContent message="This book has no chapters yet" hint="Import its chapters with the rules importer." />
      </div>
    );
  }

  const index = chapters.findIndex((entry) => entry.id === chapterId);
  const previous = index > 0 ? chapters[index - 1] : null;
  const next = index >= 0 && index < chapters.length - 1 ? chapters[index + 1] : null;
  const summary = index >= 0 ? chapters[index] : null;

  const contents = (
    <RuleContents
      book={book.data}
      currentChapterId={chapterId}
      headings={headings}
      onNavigate={() => setContentsOpen(false)}
    />
  );

  return (
    <div className="space-y-5">
      <header className="flex flex-col gap-3 border-b border-slate-200 pb-3">
        <BackToLibrary />
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="min-w-0">
            <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">{book.data.title}</p>
            <h2 className="text-2xl font-semibold tracking-tight text-slate-900 md:text-3xl">
              {summary ? `${summary.sortOrder}. ${summary.title}` : chapter.status === "ready" ? chapter.data.title : "Chapter"}
            </h2>
          </div>
          <button
            type="button"
            onClick={() => setContentsOpen(true)}
            className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50 md:hidden"
          >
            <ListTree size={16} /> Contents
          </button>
        </div>
        <RuleSearch bookId={bookId} onSelect={openSearchResult} />
      </header>

      <div className="grid gap-4 md:grid-cols-[260px_minmax(0,1fr)]">
        <aside className="hidden md:block">
          <div className="sticky top-4 max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-2xl border border-slate-200 bg-slate-50 p-3 shadow-sm">
            <p className="mb-2 px-2 text-[11px] font-bold uppercase tracking-[0.18em] text-slate-500">Contents</p>
            {contents}
          </div>
        </aside>

        <main className="min-w-0 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm md:p-6">
          {chapter.status === "loading" && <LoadingPanel message="Loading chapter…" />}
          {chapter.status === "error" && <ErrorPanel message={chapter.message} onRetry={retry} />}
          {chapter.status === "ready" && (
            <ChapterBody chapter={chapter.data} onOpenEntity={setSelection} />
          )}

          <nav className="mt-8 flex items-center justify-between gap-3 border-t border-slate-200 pt-4" aria-label="Chapter navigation">
            {previous ? (
              <Link
                to={`/library/books/${bookId}/chapters/${previous.id}`}
                className="flex min-w-0 items-center gap-1 rounded-lg px-2 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
              >
                <ChevronLeft size={16} className="shrink-0" />
                <span className="truncate">{previous.title}</span>
              </Link>
            ) : <span />}
            {next && (
              <Link
                to={`/library/books/${bookId}/chapters/${next.id}`}
                className="flex min-w-0 items-center gap-1 rounded-lg px-2 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
              >
                <span className="truncate">{next.title}</span>
                <ChevronRight size={16} className="shrink-0" />
              </Link>
            )}
          </nav>
        </main>
      </div>

      {contentsOpen && <ContentsSheet onClose={() => setContentsOpen(false)}>{contents}</ContentsSheet>}
      <RuleEntityDetails selection={selection} onClose={closeDetails} />
    </div>
  );
}

/** Every section of a chapter in reading order, each under its heading (anchored by slug). */
function ChapterBody({ chapter, onOpenEntity }: { chapter: RuleChapter; onOpenEntity: (selection: RuleEntitySelection) => void }) {
  if (chapter.sections.length === 0) {
    return <NoContent message="This chapter is empty" />;
  }
  return (
    <article>
      {chapter.sections.map((section) => {
        const level = Math.min(Math.max(section.level, 1), 6);
        // The page title is the <h2>, so section headings start at <h3>.
        const Heading = `h${Math.min(level + 2, 6)}` as "h3" | "h4" | "h5" | "h6";
        return (
          <section
            key={section.id}
            id={sectionAnchorId(section.slug)}
            className="scroll-mt-24"
          >
            <Heading className={`${HEADING_CLASSES[level]} ${level <= 2 ? "mt-8 first:mt-0" : "mt-6"} mb-2`}>
              {section.title}
            </Heading>
            {section.body && <RuleMarkdown body={section.body} chapter={chapter} onOpenEntity={onOpenEntity} />}
          </section>
        );
      })}
    </article>
  );
}

function BackToLibrary() {
  return (
    <Link to="/library" className="flex w-fit items-center gap-1 text-sm font-medium text-slate-500 hover:text-slate-800">
      <ArrowLeft size={16} /> Library
    </Link>
  );
}

function LoadingPanel({ message }: { message: string }) {
  return (
    <div className="flex min-h-[180px] items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-slate-600">
      <div className="flex items-center gap-2">
        <Spinner className="h-5 w-5" />
        <span>{message}</span>
      </div>
    </div>
  );
}

function ErrorPanel({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
      <p>{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-3 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium hover:bg-red-100"
      >
        Try again
      </button>
    </div>
  );
}

/** Bottom sheet holding the contents on phones; fixed height like the compendium sheets. */
function ContentsSheet({ onClose, children }: { onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [onClose]);

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/60 backdrop-blur-sm" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Contents"
        className="h-[85dvh] w-full overflow-y-auto overscroll-contain rounded-t-2xl border border-slate-200 bg-white p-4 pb-[max(1rem,env(safe-area-inset-bottom))] shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="mx-auto mb-3 h-1.5 w-10 rounded-full bg-slate-300" aria-hidden="true" />
        <p className="mb-2 px-2 text-[11px] font-bold uppercase tracking-[0.18em] text-slate-500">Contents</p>
        {children}
      </div>
    </div>,
    document.body,
  );
}

export default RuleBookReader;
