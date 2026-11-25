import { useCallback, useEffect, useState } from "react"
import { BookOpen, ChevronRight } from "lucide-react"
import { Link } from "react-router-dom"
import { getRuleBooks } from "../../../apis/rulesApi"
import type { RuleBook } from "../../../models/Rules"
import { Badge } from "@/components/ui/badge"
import { Spinner } from "@/components/ui/spinner"
import { NoContent } from "../../compendium/components/NoContent"

/** The Library's rulebooks: one card per book, opening the reader at its first chapter. */
export function RuleBookShelf() {
  const [books, setBooks] = useState<RuleBook[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getRuleBooks(controller.signal)
      .then((found) => {
        setBooks(found)
        setError(null)
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Could not load books")
      })
    return () => controller.abort()
  }, [attempt])

  const retry = useCallback(() => {
    setError(null)
    setBooks(null)
    setAttempt((value) => value + 1)
  }, [])

  return (
    <section className="space-y-3" aria-labelledby="rulebooks-heading">
      <h3 id="rulebooks-heading" className="text-[11px] font-bold uppercase tracking-[0.18em] text-slate-500">
        Rulebooks
      </h3>

      {error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          <p>{error}</p>
          <button
            type="button"
            onClick={retry}
            className="mt-3 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium hover:bg-red-100"
          >
            Try again
          </button>
        </div>
      ) : books === null ? (
        <div className="flex min-h-[120px] items-center justify-center gap-2 rounded-xl border border-slate-200 bg-slate-50 text-slate-600">
          <Spinner className="h-5 w-5" /> <span>Loading books…</span>
        </div>
      ) : books.length === 0 ? (
        <NoContent message="No rulebooks yet" hint="Import the rules with the rules importer." />
      ) : (
        <div className="grid gap-3.5 sm:grid-cols-2 xl:grid-cols-3">
          {books.map((book) => (
            <Link
              key={book.id}
              to={`/library/books/${book.id}`}
              className="group flex items-start gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition-colors hover:border-violet-300 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-500"
            >
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-violet-100 text-violet-700">
                <BookOpen size={20} />
              </span>
              <span className="min-w-0 flex-1">
                <Badge variant="secondary">{book.contentSourceName}</Badge>
                <span className="mt-2 block text-lg font-semibold leading-tight text-slate-900">{book.title}</span>
                {book.description && (
                  <span className="mt-1 line-clamp-2 block text-sm text-slate-600">{book.description}</span>
                )}
                <span className="mt-2 block text-xs text-slate-500">
                  {book.chapters.length} {book.chapters.length === 1 ? "chapter" : "chapters"}
                </span>
              </span>
              <ChevronRight size={18} className="mt-1 shrink-0 text-slate-400 group-hover:text-violet-600" />
            </Link>
          ))}
        </div>
      )}
    </section>
  )
}
