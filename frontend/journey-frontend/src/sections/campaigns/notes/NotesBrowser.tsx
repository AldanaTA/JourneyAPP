import { useEffect, useState } from "react";
import { ArrowLeft, ChevronRight, FilePlus2, FileText, Folder, FolderPlus, Pencil, Search, Trash2 } from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { CampaignNote, NoteSearchResult, NoteTree } from "@/apis/campaignApi";
import { createFolder, createNote, deleteFolder, deleteNote, getNote, getNoteTree, searchNotes, updateFolder, updateNote } from "@/apis/campaignApi";
import { cn } from "@/lib/utils";
import { ConfirmDialog, EmptyNote, Modal, PrimaryButton, SecondaryButton, inputClass } from "../../characters/editor/ui";
import { useCampaign } from "../campaignStore";
import { Text } from "../forms/FormKit";

/** Shows entity links ([Name](trait:id)) as plain names; other markdown as usual. */
function NoteMarkdown({ body }: { body: string }) {
  const text = body.replace(/\[([^\]]+)\]\((?:trait|power|item|race|job|creature):[^)]*\)/g, "**$1**");
  return (
    <div className="space-y-2 text-sm text-slate-800 [&_a]:text-violet-700 [&_h1]:text-lg [&_h1]:font-semibold [&_h2]:text-base [&_h2]:font-semibold [&_h3]:font-semibold [&_ol]:list-decimal [&_ol]:pl-5 [&_table]:text-xs [&_td]:border [&_td]:px-2 [&_th]:border [&_th]:px-2 [&_ul]:list-disc [&_ul]:pl-5">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{text}</ReactMarkdown>
    </div>
  );
}

/**
 * The storytellers' notes: folders, search, and a note editor with a preview. {@code compact} for a play window.
 */
export default function NotesBrowser({ compact = false }: { compact?: boolean }) {
  const campaign = useCampaign();
  const [tree, setTree] = useState<NoteTree | null>(null);
  const [folderId, setFolderId] = useState<string | null>(null);
  const [noteId, setNoteId] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [results, setResults] = useState<NoteSearchResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dialog, setDialog] = useState<"folder" | "note" | "rename" | "deleteFolder" | null>(null);
  const [name, setName] = useState("");

  useEffect(() => {
    getNoteTree(campaign.id).then(setTree).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Could not load the notes"));
  }, [campaign.id]);

  useEffect(() => {
    const q = search.trim();
    if (!q) return;
    let cancelled = false;
    const timer = window.setTimeout(() => {
      searchNotes(campaign.id, q).then((r) => !cancelled && setResults(r)).catch(() => undefined);
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [search, campaign.id]);

  const fail = (reason: unknown) => setError(reason instanceof Error ? reason.message : "Something went wrong");
  const reloadTree = () => getNoteTree(campaign.id).then(setTree).catch(fail);

  if (noteId) {
    return (
      <NoteEditor
        noteId={noteId}
        folders={tree?.folders ?? []}
        onBack={() => {
          setNoteId(null);
          void reloadTree();
        }}
        compact={compact}
      />
    );
  }

  const folder = tree?.folders.find((f) => f.id === folderId) ?? null;
  const path: { id: string; name: string }[] = [];
  for (let f = folder; f; f = tree?.folders.find((x) => x.id === f?.parentId) ?? null) path.unshift({ id: f.id, name: f.name });
  const subfolders = (tree?.folders ?? []).filter((f) => f.parentId === folderId).sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
  const notes = (tree?.notes ?? []).filter((n) => n.folderId === folderId).sort((a, b) => a.sortOrder - b.sortOrder || a.title.localeCompare(b.title));
  const searching = search.trim().length > 0;

  return (
    <div className="space-y-3">
      {!compact && (
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">Storytellers only</p>
          <h2 className="text-2xl font-semibold tracking-tight text-slate-900">Notes</h2>
        </div>
      )}
      <div className="relative">
        <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search notes" aria-label="Search notes" className={cn(inputClass, "py-1.5 pl-8")} />
      </div>
      {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

      {searching ? (
        <ul className="space-y-1.5">
          {results?.length === 0 && <EmptyNote>No note matches.</EmptyNote>}
          {results?.map((r) => (
            <li key={r.id}>
              <button type="button" onClick={() => setNoteId(r.id)} className="block w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-left hover:border-violet-300">
                <span className="block text-sm font-semibold text-slate-900">{r.title}</span>
                <span className="block text-xs text-slate-500">{r.snippet.replace(/«|»/g, "")}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-1 text-sm">
            <button type="button" onClick={() => setFolderId(null)} className={cn("font-medium", folderId ? "text-violet-700" : "text-slate-900")}>All notes</button>
            {path.map((p) => (
              <span key={p.id} className="flex items-center gap-1">
                <ChevronRight size={14} className="text-slate-400" />
                <button type="button" onClick={() => setFolderId(p.id)} className={cn("font-medium", p.id === folderId ? "text-slate-900" : "text-violet-700")}>{p.name}</button>
              </span>
            ))}
            {folder && (
              <span className="ml-auto flex gap-1">
                <button type="button" aria-label="Rename folder" onClick={() => { setName(folder.name); setDialog("rename"); }} className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100"><Pencil size={14} /></button>
                <button type="button" aria-label="Delete folder" onClick={() => setDialog("deleteFolder")} className="rounded-lg p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600"><Trash2 size={14} /></button>
              </span>
            )}
          </div>
          <ul className="space-y-1.5">
            {subfolders.map((f) => (
              <li key={f.id}>
                <button type="button" onClick={() => setFolderId(f.id)} className="flex w-full items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-left hover:border-violet-300">
                  <Folder size={16} className="text-amber-500" />
                  <span className="flex-1 text-sm font-medium text-slate-900">{f.name}</span>
                  <ChevronRight size={15} className="text-slate-400" />
                </button>
              </li>
            ))}
            {notes.map((n) => (
              <li key={n.id}>
                <button type="button" onClick={() => setNoteId(n.id)} className="flex w-full items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-left hover:border-violet-300">
                  <FileText size={16} className="text-violet-500" />
                  <span className="flex-1 text-sm font-medium text-slate-900">{n.title}</span>
                </button>
              </li>
            ))}
            {tree && subfolders.length === 0 && notes.length === 0 && <EmptyNote>Nothing here yet.</EmptyNote>}
          </ul>
          <div className="flex gap-2">
            <SecondaryButton onClick={() => { setName(""); setDialog("folder"); }}><FolderPlus size={15} /> Folder</SecondaryButton>
            <PrimaryButton onClick={() => { setName(""); setDialog("note"); }}><FilePlus2 size={15} /> Note</PrimaryButton>
          </div>
        </>
      )}

      {(dialog === "folder" || dialog === "note" || dialog === "rename") && (
        <Modal
          title={dialog === "folder" ? "New folder" : dialog === "note" ? "New note" : "Rename folder"}
          onClose={() => setDialog(null)}
          footer={
            <>
              <SecondaryButton onClick={() => setDialog(null)}>Cancel</SecondaryButton>
              <PrimaryButton
                disabled={!name.trim()}
                onClick={() => {
                  const which = dialog;
                  setDialog(null);
                  if (which === "folder") createFolder(campaign.id, { name: name.trim(), parent_id: folderId ?? "" }).then(setTree).catch(fail);
                  else if (which === "rename" && folderId) updateFolder(campaign.id, folderId, { name: name.trim() }).then(setTree).catch(fail);
                  else createNote(campaign.id, { title: name.trim(), folder_id: folderId ?? "" }).then((n) => setNoteId(n.id)).catch(fail);
                }}
              >
                {dialog === "rename" ? "Rename" : "Create"}
              </PrimaryButton>
            </>
          }
        >
          <Text label={dialog === "note" ? "Title" : "Name"} value={name} onChange={setName} />
        </Modal>
      )}
      {dialog === "deleteFolder" && folder && (
        <ConfirmDialog
          title={`Delete ${folder.name}?`}
          message="Everything in it, folders and notes, is deleted."
          confirmLabel="Delete"
          onClose={() => setDialog(null)}
          onConfirm={() => {
            setDialog(null);
            setFolderId(folder.parentId);
            deleteFolder(campaign.id, folder.id).then(setTree).catch(fail);
          }}
        />
      )}
    </div>
  );
}

function NoteEditor({ noteId, folders, onBack, compact }: {
  noteId: string;
  folders: NoteTree["folders"];
  onBack: () => void;
  compact: boolean;
}) {
  const campaign = useCampaign();
  const [note, setNote] = useState<CampaignNote | null>(null);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [folderId, setFolderId] = useState("");
  const [preview, setPreview] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const [confirm, setConfirm] = useState(false);

  useEffect(() => {
    getNote(campaign.id, noteId)
      .then((n) => {
        setNote(n);
        setTitle(n.title);
        setBody(n.body);
        setFolderId(n.folderId ?? "");
      })
      .catch((reason: unknown) => setMessage({ ok: false, text: reason instanceof Error ? reason.message : "Could not load the note" }));
  }, [campaign.id, noteId]);

  const dirty = note !== null && (title !== note.title || body !== note.body || folderId !== (note.folderId ?? ""));
  const save = async () => {
    setSaving(true);
    setMessage(null);
    try {
      const saved = await updateNote(campaign.id, noteId, { title: title.trim() || note?.title, body, folder_id: folderId });
      setNote(saved);
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
        <button type="button" onClick={onBack} className="flex items-center gap-1 text-sm font-medium text-violet-700"><ArrowLeft size={15} /> Notes</button>
        <span className="flex items-center gap-2">
          <SecondaryButton className="px-2.5 py-1.5" onClick={() => setPreview(!preview)}>{preview ? "Edit" : "Preview"}</SecondaryButton>
          <PrimaryButton disabled={!dirty || saving} onClick={() => void save()}>Save</PrimaryButton>
        </span>
      </div>
      {message && <p className={cn("rounded-lg px-3 py-2 text-sm", message.ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-700")}>{message.text}</p>}
      {note === null ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : (
        <>
          <input value={title} onChange={(e) => setTitle(e.target.value)} aria-label="Note title" className={cn(inputClass, "text-lg font-semibold")} />
          {preview ? (
            <div className="min-h-[200px] rounded-xl border border-slate-200 bg-white p-4"><NoteMarkdown body={body} /></div>
          ) : (
            <textarea value={body} onChange={(e) => setBody(e.target.value)} rows={compact ? 10 : 18} aria-label="Note text" placeholder="Write in markdown…" className={cn(inputClass, "resize-y font-mono text-sm")} />
          )}
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
            <label className="flex items-center gap-2">
              Folder
              <select value={folderId} onChange={(e) => setFolderId(e.target.value)} className="rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs">
                <option value="">Top level</option>
                {folders.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
              </select>
            </label>
            <span>{note.editedByName ? `Last edited by ${note.editedByName}` : ""}</span>
            <button type="button" onClick={() => setConfirm(true)} className="flex items-center gap-1 font-semibold text-red-700"><Trash2 size={13} /> Delete note</button>
          </div>
        </>
      )}
      {confirm && (
        <ConfirmDialog
          title="Delete this note?"
          message="It cannot be brought back."
          confirmLabel="Delete"
          onClose={() => setConfirm(false)}
          onConfirm={() => {
            setConfirm(false);
            deleteNote(campaign.id, noteId).then(onBack).catch((reason: unknown) => setMessage({ ok: false, text: reason instanceof Error ? reason.message : "Could not delete" }));
          }}
        />
      )}
    </div>
  );
}
