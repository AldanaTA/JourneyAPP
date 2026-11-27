import { useEffect, useState } from "react";
import { ArrowDown, ArrowLeft, ArrowUp, BookOpen, ExternalLink, Link2, Plus, Trash2, X } from "lucide-react";
import { Link } from "react-router-dom";
import {
  addChapter,
  createBook,
  deleteBook,
  listCampaignBooks,
  removeChapter,
  reorderChapters,
  saveChapter,
  updateBook,
} from "@/apis/campaignApi";
import { getRuleBooks, getRuleChapter } from "@/apis/rulesApi";
import type { RuleBook } from "@/models/Rules";
import { cn } from "@/lib/utils";
import { ConfirmDialog, EmptyNote, Fab, Modal, Panel, PrimaryButton, SecondaryButton, inputClass } from "../../characters/editor/ui";
import { useCampaign } from "../campaignStore";
import { Area, Text } from "../forms/FormKit";

/** The campaign's own rule books: books, their chapters, and the chapter editor. */
export default function RulesTab() {
  const campaign = useCampaign();
  const [books, setBooks] = useState<RuleBook[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [bookId, setBookId] = useState<string | null>(null);
  const [chapterId, setChapterId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const reload = () =>
    listCampaignBooks(campaign.id)
      .then(setBooks)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Could not load the rules"));
  useEffect(() => {
    void reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [campaign.id]);

  const book = books?.find((b) => b.id === bookId) ?? null;
  const replaceBook = (updated: RuleBook) => setBooks((list) => list?.map((b) => (b.id === updated.id ? updated : b)) ?? null);

  if (book && chapterId) {
    return <ChapterEditor key={chapterId} bookId={book.id} chapterId={chapterId} onBack={() => {
      setChapterId(null);
      void reload();
    }} />;
  }
  if (book) {
    return (
      <BookView
        book={book}
        onBack={() => setBookId(null)}
        onChanged={replaceBook}
        onDeleted={() => {
          setBookId(null);
          void reload();
        }}
        onOpenChapter={setChapterId}
      />
    );
  }

  return (
    <div className="space-y-3">
      <div>
        <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">Campaign content</p>
        <h2 className="text-2xl font-semibold tracking-tight text-slate-900">Rules</h2>
        <p className="text-sm text-slate-600">House rules and setting books for this campaign. Players read them in the library reader.</p>
      </div>
      {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      {books?.length === 0 && <EmptyNote>No rule books yet. Tap + to start one.</EmptyNote>}
      <ul className="space-y-1.5">
        {books?.map((b) => (
          <li key={b.id}>
            <button
              type="button"
              onClick={() => setBookId(b.id)}
              className="flex w-full items-center gap-3 rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-left shadow-sm hover:border-violet-300"
            >
              <BookOpen size={18} className="shrink-0 text-violet-600" />
              <span className="min-w-0 flex-1">
                <span className="block font-semibold text-slate-900">{b.title}</span>
                <span className="block text-xs text-slate-500">{b.chapters.length} chapter{b.chapters.length === 1 ? "" : "s"}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
      <Fab label="New rule book" onClick={() => setCreating(true)} className="bottom-24" />
      {creating && (
        <BookModal
          onClose={() => setCreating(false)}
          onSave={async (title, description) => {
            const created = await createBook(campaign.id, title, description);
            setCreating(false);
            await reload();
            setBookId(created.id);
          }}
        />
      )}
    </div>
  );
}

function BookModal({ book, onSave, onClose }: { book?: RuleBook; onSave: (title: string, description: string) => Promise<void>; onClose: () => void }) {
  const [title, setTitle] = useState(book?.title ?? "");
  const [description, setDescription] = useState(book?.description ?? "");
  const [error, setError] = useState<string | null>(null);
  return (
    <Modal
      title={book ? "Edit book" : "New rule book"}
      onClose={onClose}
      footer={
        <>
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          <PrimaryButton
            disabled={!title.trim()}
            onClick={() => onSave(title.trim(), description).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Could not save"))}
          >
            Save
          </PrimaryButton>
        </>
      }
    >
      <div className="space-y-3">
        <Text label="Title" value={title} onChange={setTitle} placeholder="Coast House Rules" />
        <Area label="Description" value={description} onChange={setDescription} />
      </div>
      {error && <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
    </Modal>
  );
}

function BookView({ book, onBack, onChanged, onDeleted, onOpenChapter }: {
  book: RuleBook;
  onBack: () => void;
  onChanged: (book: RuleBook) => void;
  onDeleted: () => void;
  onOpenChapter: (chapterId: string) => void;
}) {
  const campaign = useCampaign();
  const [error, setError] = useState<string | null>(null);
  const [dialog, setDialog] = useState<"edit" | "new" | "link" | "delete" | null>(null);
  const [newTitle, setNewTitle] = useState("");
  const run = (work: () => Promise<RuleBook>) =>
    work().then(onChanged).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Something went wrong"));
  const chapters = [...book.chapters].sort((a, b) => a.sortOrder - b.sortOrder);
  const move = (index: number, by: number) => {
    const order = chapters.map((c) => c.id);
    const [moved] = order.splice(index, 1);
    order.splice(index + by, 0, moved);
    void run(() => reorderChapters(campaign.id, book.id, order));
  };

  return (
    <div className="space-y-3">
      <button type="button" onClick={onBack} className="flex items-center gap-1 text-sm font-medium text-violet-700">
        <ArrowLeft size={15} /> All rule books
      </button>
      <Panel
        title={book.title}
        action={
          <span className="flex gap-1">
            <SecondaryButton className="px-2.5 py-1 text-xs" onClick={() => setDialog("edit")}>Edit</SecondaryButton>
            <Link to={`/library/books/${book.id}`} className="flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50">
              <ExternalLink size={13} /> Reader
            </Link>
          </span>
        }
      >
        {book.description && <p className="mb-3 text-sm text-slate-600">{book.description}</p>}
        {chapters.length === 0 ? (
          <EmptyNote>No chapters yet.</EmptyNote>
        ) : (
          <ul className="divide-y divide-slate-100">
            {chapters.map((c, i) => {
              const own = c.contentSourceName === campaign.name;
              return (
                <li key={c.id} className="flex items-center gap-2 py-2">
                  <button type="button" disabled={!own} onClick={() => onOpenChapter(c.id)} className="min-w-0 flex-1 text-left disabled:cursor-default">
                    <span className="block truncate text-sm font-medium text-slate-900">{c.title}</span>
                    <span className="block text-xs text-slate-500">{own ? "Tap to edit" : `From ${c.contentSourceName} (read-only)`}</span>
                  </button>
                  <button type="button" aria-label="Move up" disabled={i === 0} onClick={() => move(i, -1)} className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 disabled:opacity-30">
                    <ArrowUp size={15} />
                  </button>
                  <button type="button" aria-label="Move down" disabled={i === chapters.length - 1} onClick={() => move(i, 1)} className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 disabled:opacity-30">
                    <ArrowDown size={15} />
                  </button>
                  <button type="button" aria-label={own ? "Delete chapter" : "Take out of the book"} onClick={() => void run(() => removeChapter(campaign.id, book.id, c.id))} className="rounded-lg p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600">
                    {own ? <Trash2 size={15} /> : <X size={15} />}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
        <div className="mt-3 flex flex-wrap gap-2">
          <PrimaryButton onClick={() => setDialog("new")}><Plus size={15} /> New chapter</PrimaryButton>
          <SecondaryButton onClick={() => setDialog("link")}><Link2 size={15} /> Include an official chapter</SecondaryButton>
          <button type="button" onClick={() => setDialog("delete")} className="ml-auto rounded-lg px-2 py-2 text-sm font-semibold text-red-700 hover:bg-red-50">Delete book</button>
        </div>
        {error && <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      </Panel>

      {dialog === "edit" && (
        <BookModal book={book} onClose={() => setDialog(null)} onSave={async (title, description) => {
          onChanged(await updateBook(campaign.id, book.id, { title, description }));
          setDialog(null);
        }} />
      )}
      {dialog === "new" && (
        <Modal title="New chapter" onClose={() => setDialog(null)} footer={
          <>
            <SecondaryButton onClick={() => setDialog(null)}>Cancel</SecondaryButton>
            <PrimaryButton disabled={!newTitle.trim()} onClick={() => {
              setDialog(null);
              void run(() => addChapter(campaign.id, book.id, { title: newTitle.trim() }));
              setNewTitle("");
            }}>Add</PrimaryButton>
          </>
        }>
          <Text label="Title" value={newTitle} onChange={setNewTitle} placeholder="Sailing" />
        </Modal>
      )}
      {dialog === "link" && (
        <LinkChapterModal exclude={new Set(book.chapters.map((c) => c.id))} onClose={() => setDialog(null)} onPick={(chapterId) => {
          setDialog(null);
          void run(() => addChapter(campaign.id, book.id, { chapter_id: chapterId }));
        }} />
      )}
      {dialog === "delete" && (
        <ConfirmDialog
          title={`Delete ${book.title}?`}
          message="Its own chapters are deleted too; included official chapters are not."
          confirmLabel="Delete"
          onClose={() => setDialog(null)}
          onConfirm={() => {
            setDialog(null);
            deleteBook(campaign.id, book.id).then(onDeleted).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Could not delete"));
          }}
        />
      )}
    </div>
  );
}

/** Pick an official chapter (core rules, books) to include by reference. */
function LinkChapterModal({ exclude, onPick, onClose }: { exclude: Set<string>; onPick: (chapterId: string) => void; onClose: () => void }) {
  const [books, setBooks] = useState<RuleBook[] | null>(null);
  useEffect(() => {
    getRuleBooks().then(setBooks).catch(() => setBooks([]));
  }, []);
  return (
    <Modal title="Include an official chapter" subtitle="It stays read-only and follows updates to the official rules." onClose={onClose} wide>
      {books === null && <p className="text-sm text-slate-500">Loading…</p>}
      <div className="space-y-3">
        {books?.map((b) => (
          <div key={b.id}>
            <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500">{b.title}</h4>
            <ul className="mt-1 space-y-1">
              {[...b.chapters].sort((x, y) => x.sortOrder - y.sortOrder).map((c) => (
                <li key={c.id}>
                  <button type="button" disabled={exclude.has(c.id)} onClick={() => onPick(c.id)} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-left text-sm hover:border-violet-300 disabled:opacity-40">
                    {c.title}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </Modal>
  );
}

interface EditSection {
  key: string;
  id?: string;
  title: string;
  level: number;
  body: string;
}

/**
 * Edits one campaign chapter: its sections in reading order, each with a heading level (the tree comes from the
 * levels) and a markdown body. Nothing is sent until Save, which sends the whole chapter.
 */
function ChapterEditor({ bookId, chapterId, onBack }: { bookId: string; chapterId: string; onBack: () => void }) {
  const campaign = useCampaign();
  const [title, setTitle] = useState("");
  const [sections, setSections] = useState<EditSection[] | null>(null);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    getRuleChapter(chapterId)
      .then((chapter) => {
        setTitle(chapter.title);
        setSections(chapter.sections.map((s) => ({ key: s.id, id: s.id, title: s.title, level: s.level, body: s.body })));
      })
      .catch((reason: unknown) => setMessage({ ok: false, text: reason instanceof Error ? reason.message : "Could not load the chapter" }));
  }, [chapterId]);

  const change = (recipe: (list: EditSection[]) => EditSection[]) => {
    setSections((list) => (list ? recipe(list) : list));
    setDirty(true);
  };
  const edit = (index: number, patch: Partial<EditSection>) => change((list) => list.map((s, i) => (i === index ? { ...s, ...patch } : s)));
  const save = async () => {
    if (!sections) return;
    setSaving(true);
    setMessage(null);
    try {
      const saved = await saveChapter(campaign.id, chapterId, {
        title: title.trim(),
        sections: sections.map((s) => ({ id: s.id, title: s.title.trim() || "Untitled", level: s.level, body: s.body })),
      });
      setSections(saved.sections.map((s) => ({ key: s.id, id: s.id, title: s.title, level: s.level, body: s.body })));
      setDirty(false);
      setMessage({ ok: true, text: "Saved" });
    } catch (reason) {
      setMessage({ ok: false, text: reason instanceof Error ? reason.message : "Could not save" });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <button type="button" onClick={onBack} className="flex items-center gap-1 text-sm font-medium text-violet-700">
          <ArrowLeft size={15} /> Back to the book
        </button>
        <span className="flex items-center gap-2">
          <Link to={`/library/books/${bookId}/chapters/${chapterId}`} className="flex items-center gap-1 text-xs font-medium text-slate-600 hover:text-violet-700">
            <ExternalLink size={13} /> Reader
          </Link>
          <PrimaryButton disabled={!dirty || saving} onClick={() => void save()}>Save chapter</PrimaryButton>
        </span>
      </div>
      {message && (
        <p className={cn("rounded-lg px-3 py-2 text-sm", message.ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-700")}>{message.text}</p>
      )}
      <input
        value={title}
        onChange={(e) => {
          setTitle(e.target.value);
          setDirty(true);
        }}
        aria-label="Chapter title"
        className={cn(inputClass, "text-lg font-semibold")}
      />
      <p className="text-xs text-slate-500">
        Write in markdown. Link content with [Name](trait:id), (power:id), (item:id), (race:id), (job:id), or (creature:id);
        the reader shows them as cards.
      </p>
      {sections === null ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : (
        <ul className="space-y-2">
          {sections.map((s, i) => (
            <li key={s.key} className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm" style={{ marginLeft: `${(s.level - 1) * 12}px` }}>
              <div className="flex items-center gap-2">
                <select
                  value={s.level}
                  aria-label="Heading level"
                  onChange={(e) => edit(i, { level: Number(e.target.value) })}
                  className="rounded-lg border border-slate-200 bg-white px-1.5 py-1.5 text-xs font-semibold"
                >
                  {[1, 2, 3, 4, 5, 6].map((l) => <option key={l} value={l}>H{l}</option>)}
                </select>
                <input
                  value={s.title}
                  onChange={(e) => edit(i, { title: e.target.value })}
                  aria-label="Section title"
                  placeholder="Section title"
                  className={cn(inputClass, "py-1.5 font-semibold")}
                />
                <button type="button" aria-label="Move up" disabled={i === 0} onClick={() => change((list) => swap(list, i, i - 1))} className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 disabled:opacity-30"><ArrowUp size={15} /></button>
                <button type="button" aria-label="Move down" disabled={i === sections.length - 1} onClick={() => change((list) => swap(list, i, i + 1))} className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 disabled:opacity-30"><ArrowDown size={15} /></button>
                <button type="button" aria-label="Delete section" onClick={() => change((list) => list.filter((_, j) => j !== i))} className="rounded-lg p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600"><Trash2 size={15} /></button>
              </div>
              <textarea
                value={s.body}
                onChange={(e) => edit(i, { body: e.target.value })}
                rows={Math.min(14, Math.max(3, s.body.split("\n").length + 1))}
                aria-label="Section text"
                className={cn(inputClass, "mt-2 resize-y font-mono text-sm")}
              />
              <button
                type="button"
                onClick={() => change((list) => [...list.slice(0, i + 1), { key: crypto.randomUUID(), title: "", level: s.level, body: "" }, ...list.slice(i + 1)])}
                className="mt-1 flex items-center gap-1 text-xs font-medium text-violet-700"
              >
                <Plus size={13} /> Section below
              </button>
            </li>
          ))}
        </ul>
      )}
      {sections?.length === 0 && (
        <SecondaryButton onClick={() => change(() => [{ key: crypto.randomUUID(), title: title || "Introduction", level: 1, body: "" }])}>
          <Plus size={15} /> First section
        </SecondaryButton>
      )}
    </div>
  );
}

function swap<T>(list: T[], a: number, b: number): T[] {
  const copy = [...list];
  [copy[a], copy[b]] = [copy[b], copy[a]];
  return copy;
}
