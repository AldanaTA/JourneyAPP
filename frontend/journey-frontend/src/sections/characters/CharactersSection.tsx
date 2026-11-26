import { useEffect, useMemo, useState } from "react";
import { Eye, Pencil, Search, Trash2 } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { deleteCharacter, listCharacters } from "@/apis/characterApi";
import { Spinner } from "@/components/ui/spinner";
import type { CharacterSummary } from "@/models/Character";
import { ConfirmDialog, Fab, inputClass } from "./editor/ui";

type Sort = "name-asc" | "name-desc" | "created-asc" | "updated-asc";

const SORTS: { value: Sort; label: string }[] = [
  { value: "name-asc", label: "Name (A–Z)" },
  { value: "name-desc", label: "Name (Z–A)" },
  { value: "created-asc", label: "Date created" },
  { value: "updated-asc", label: "Last updated" },
];

function compare(sort: Sort) {
  return (a: CharacterSummary, b: CharacterSummary) => {
    switch (sort) {
      case "name-asc":
        return a.name.localeCompare(b.name);
      case "name-desc":
        return b.name.localeCompare(a.name);
      case "created-asc":
        return a.createdAt.localeCompare(b.createdAt);
      case "updated-asc":
        return a.updatedAt.localeCompare(b.updatedAt);
    }
  };
}

const AVATAR_COLORS = [
  "from-emerald-400 to-emerald-500",
  "from-violet-400 to-violet-500",
  "from-amber-400 to-orange-500",
  "from-sky-400 to-indigo-500",
  "from-rose-400 to-pink-500",
];

/** The signed-in user's characters: search by name, sort, view, edit, delete, and + to create one. */
function CharactersSection() {
  const navigate = useNavigate();
  const [characters, setCharacters] = useState<CharacterSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<Sort>("name-asc");
  const [deleting, setDeleting] = useState<CharacterSummary | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listCharacters(controller.signal)
      .then(setCharacters)
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Could not load your characters");
      });
    return () => controller.abort();
  }, []);

  const shown = useMemo(() => {
    const query = search.trim().toLowerCase();
    return (characters ?? []).filter((c) => c.name.toLowerCase().includes(query)).sort(compare(sort));
  }, [characters, search, sort]);

  const remove = async (character: CharacterSummary) => {
    setDeleting(null);
    try {
      await deleteCharacter(character.id);
      setCharacters((list) => list?.filter((c) => c.id !== character.id) ?? null);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not delete the character");
    }
  };

  return (
    <div className="space-y-4 pb-20">
      <header className="pt-1">
        <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">Roster</p>
        <h2 className="m-0 text-3xl font-semibold tracking-tight text-slate-900">Characters</h2>
      </header>

      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search by name"
            aria-label="Search characters by name"
            className={`${inputClass} py-1.5 pl-8`}
          />
        </div>
        <select
          value={sort}
          onChange={(event) => setSort(event.target.value as Sort)}
          aria-label="Sort characters"
          className="rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-700 outline-none focus:border-violet-400"
        >
          {SORTS.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
      </div>

      {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

      {characters === null && !error ? (
        <div className="flex items-center justify-center gap-2 py-10 text-slate-500">
          <Spinner className="h-5 w-5" /> Loading…
        </div>
      ) : shown.length === 0 && characters ? (
        <p className="rounded-2xl border border-dashed border-slate-300 bg-white/60 px-4 py-8 text-center text-sm text-slate-500">
          {characters.length === 0 ? "No characters yet. Tap + to create one." : "No character has that name."}
        </p>
      ) : (
        <ul className="space-y-2">
          {shown.map((c, index) => (
            <li key={c.id} className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-3 shadow-sm">
              <div
                className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br text-sm font-bold uppercase text-white ${AVATAR_COLORS[index % AVATAR_COLORS.length]}`}
              >
                {c.name.charAt(0)}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <strong className="truncate text-base font-semibold text-slate-900">{c.name}</strong>
                  {c.status === "DRAFT" && <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-amber-800">Draft</span>}
                </div>
                <span className="block truncate text-sm text-slate-500">
                  {[c.raceNames.join(" / "), c.jobName].filter(Boolean).join(" · ") || "Not chosen yet"}
                </span>
              </div>
              <div className="flex shrink-0 gap-1">
                <RowButton label={`View ${c.name}`} onClick={() => navigate(`/characters/${c.id}`)}>
                  <Eye size={16} />
                </RowButton>
                <RowButton label={`Edit ${c.name}`} onClick={() => navigate(`/characters/${c.id}/edit`)}>
                  <Pencil size={16} />
                </RowButton>
                <RowButton label={`Delete ${c.name}`} onClick={() => setDeleting(c)} danger>
                  <Trash2 size={16} />
                </RowButton>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Fab label="Create a character" onClick={() => navigate("/characters/new/edit")} className="bottom-6" />

      {deleting && (
        <ConfirmDialog
          title={`Delete ${deleting.name}?`}
          message="The character, its traits, powers, and inventory are deleted for good."
          confirmLabel="Delete"
          onClose={() => setDeleting(null)}
          onConfirm={() => void remove(deleting)}
        />
      )}
    </div>
  );
}

function RowButton({ label, onClick, danger, children }: { label: string; onClick: () => void; danger?: boolean; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label.split(" ")[0]}
      className={`flex h-9 w-9 items-center justify-center rounded-lg border transition ${
        danger ? "border-red-100 text-red-600 hover:bg-red-50" : "border-slate-200 text-slate-600 hover:bg-slate-50 hover:text-slate-900"
      }`}
    >
      {children}
    </button>
  );
}

export default CharactersSection;
