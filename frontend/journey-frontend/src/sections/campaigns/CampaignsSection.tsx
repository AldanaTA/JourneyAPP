import { useEffect, useMemo, useState } from "react";
import { Crown, KeyRound, Plus, Search, Users } from "lucide-react";
import { useNavigate } from "react-router-dom";
import type { CampaignSummary, JoinPreview } from "@/apis/campaignApi";
import { createCampaign, joinCampaign, listCampaigns, previewJoin } from "@/apis/campaignApi";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import { Fab, Modal, PrimaryButton, SecondaryButton, TextField, inputClass } from "../characters/editor/ui";

const ROLE_LABELS = { OWNER: "Storyteller", STORYTELLER: "Co-storyteller", PLAYER: "Player" } as const;

type RoleTab = "all" | "storyteller" | "player";
type Sort = "latest" | "oldest" | "name-asc" | "name-desc";

const TABS: { value: RoleTab; label: string }[] = [
  { value: "all", label: "All" },
  { value: "storyteller", label: "Storyteller" },
  { value: "player", label: "Player" },
];

const SORTS: { value: Sort; label: string }[] = [
  { value: "latest", label: "Latest" },
  { value: "oldest", label: "Oldest" },
  { value: "name-asc", label: "Name (A–Z)" },
  { value: "name-desc", label: "Name (Z–A)" },
];

/** When the campaign was made (older APIs only send updatedAt). */
const createdOf = (c: CampaignSummary) => c.createdAt ?? c.updatedAt;

/** Every campaign the user runs or plays in, and + to create or join one. */
function CampaignsSection() {
  const navigate = useNavigate();
  const [campaigns, setCampaigns] = useState<CampaignSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [tab, setTab] = useState<RoleTab>("all");
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<Sort>("latest");

  const shown = useMemo(() => {
    const query = search.trim().toLowerCase();
    return (campaigns ?? [])
      .filter((c) => tab === "all" || (tab === "player" ? c.role === "PLAYER" : c.role !== "PLAYER"))
      .filter((c) => c.name.toLowerCase().includes(query))
      .sort((a, b) => {
        switch (sort) {
          case "latest":
            return createdOf(b).localeCompare(createdOf(a));
          case "oldest":
            return createdOf(a).localeCompare(createdOf(b));
          case "name-asc":
            return a.name.localeCompare(b.name);
          case "name-desc":
            return b.name.localeCompare(a.name);
        }
      });
  }, [campaigns, tab, search, sort]);
  const count = (t: RoleTab) =>
    (campaigns ?? []).filter((c) => t === "all" || (t === "player" ? c.role === "PLAYER" : c.role !== "PLAYER")).length;

  useEffect(() => {
    const controller = new AbortController();
    listCampaigns(controller.signal)
      .then(setCampaigns)
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Could not load your campaigns");
      });
    return () => controller.abort();
  }, []);

  return (
    <div className="space-y-4 pb-20">
      <header className="pt-1">
        <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">Adventures</p>
        <h2 className="m-0 text-3xl font-semibold tracking-tight text-slate-900">Campaigns</h2>
      </header>

      <div className="flex rounded-xl bg-slate-100 p-1" role="tablist" aria-label="Your role">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            role="tab"
            aria-selected={tab === t.value}
            onClick={() => setTab(t.value)}
            className={cn(
              "flex flex-1 items-center justify-center gap-1.5 rounded-lg px-2 py-1.5 text-sm font-medium transition",
              tab === t.value ? "bg-white text-slate-900 shadow-sm" : "text-slate-600 hover:text-slate-900",
            )}
          >
            {t.value === "storyteller" && <Crown size={14} />}
            {t.value === "player" && <Users size={14} />}
            {t.label}
            {campaigns && <span className="text-xs text-slate-400">{count(t.value)}</span>}
          </button>
        ))}
      </div>

      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search by name"
            aria-label="Search campaigns by name"
            className={cn(inputClass, "py-1.5 pl-8")}
          />
        </div>
        <select
          value={sort}
          onChange={(event) => setSort(event.target.value as Sort)}
          aria-label="Sort campaigns"
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
      {campaigns === null && !error ? (
        <div className="flex items-center justify-center gap-2 py-10 text-slate-500">
          <Spinner className="h-5 w-5" /> Loading…
        </div>
      ) : shown.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-slate-300 bg-white/60 px-4 py-8 text-center text-sm text-slate-500">
          {campaigns?.length === 0
            ? "No campaigns yet. Tap + to create one or join with a code."
            : search.trim()
              ? "No campaign has that name."
              : tab === "player"
                ? "You are not a player in any campaign yet. Tap + and enter a code."
                : "You are not running any campaign yet. Tap + to create one."}
        </p>
      ) : (
        <ul className="space-y-2">
          {shown.map((c) => (
            <li key={c.id}>
              <button
                type="button"
                onClick={() => navigate(`/campaigns/${c.id}`)}
                className="flex w-full items-center gap-3 rounded-2xl border border-slate-200 bg-white p-3 text-left shadow-sm transition hover:border-violet-300"
              >
                <span
                  className={cn(
                    "flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-white",
                    c.role === "PLAYER" ? "bg-gradient-to-br from-sky-400 to-indigo-500" : "bg-gradient-to-br from-violet-500 to-fuchsia-500",
                  )}
                >
                  {c.role === "PLAYER" ? <Users size={20} /> : <Crown size={20} />}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-semibold text-slate-900">{c.name}</span>
                  <span className="block truncate text-sm text-slate-500">
                    {ROLE_LABELS[c.role]} · {c.playerCount} player{c.playerCount === 1 ? "" : "s"}
                    {c.role === "PLAYER" ? ` · run by ${c.ownerName}` : ""}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}

      <Fab label="Create or join a campaign" onClick={() => setAdding(true)} className="bottom-6" />
      {adding && <AddCampaignModal onClose={() => setAdding(false)} onDone={(id) => navigate(`/campaigns/${id}`)} />}
    </div>
  );
}

/** Create a campaign (as its storyteller) or join one with a code (as a player or co-storyteller). */
function AddCampaignModal({ onClose, onDone }: { onClose: () => void; onDone: (campaignId: string) => void }) {
  const [mode, setMode] = useState<"join" | "create">("join");
  const [code, setCode] = useState("");
  const [preview, setPreview] = useState<JoinPreview | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (work: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await work();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Something went wrong");
    } finally {
      setBusy(false);
    }
  };

  // Accept a whole join link as well as a bare code.
  const cleanCode = code.trim().replace(/^.*\/join\//, "");

  return (
    <Modal
      title={mode === "join" ? "Join a campaign" : "Create a campaign"}
      onClose={onClose}
      footer={
        <>
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          {mode === "join" ? (
            preview ? (
              <PrimaryButton
                disabled={busy}
                onClick={() => run(async () => onDone((await joinCampaign(cleanCode)).id))}
              >
                {preview.alreadyMember && preview.currentRole !== "PLAYER" ? "Open" : `Join as ${preview.joinsAs === "STORYTELLER" ? "co-storyteller" : "player"}`}
              </PrimaryButton>
            ) : (
              <PrimaryButton disabled={busy || !cleanCode} onClick={() => run(async () => setPreview(await previewJoin(cleanCode)))}>
                Find campaign
              </PrimaryButton>
            )
          ) : (
            <PrimaryButton disabled={busy || !name.trim()} onClick={() => run(async () => onDone((await createCampaign(name.trim(), description)).id))}>
              Create
            </PrimaryButton>
          )}
        </>
      }
    >
      <div className="mb-4 flex rounded-xl bg-slate-100 p-1">
        {(
          [
            ["join", "Join with a code", KeyRound],
            ["create", "Create (as storyteller)", Plus],
          ] as const
        ).map(([value, label, Icon]) => (
          <button
            key={value}
            type="button"
            onClick={() => {
              setMode(value);
              setError(null);
            }}
            className={cn(
              "flex flex-1 items-center justify-center gap-1.5 rounded-lg px-2 py-2 text-sm font-medium transition",
              mode === value ? "bg-white text-slate-900 shadow-sm" : "text-slate-600",
            )}
          >
            <Icon size={15} /> {label}
          </button>
        ))}
      </div>

      {mode === "join" ? (
        <div className="space-y-3">
          <TextField
            label="Code or join link"
            value={code}
            placeholder="Paste the code your storyteller gave you"
            onChange={(value) => {
              setCode(value);
              setPreview(null);
            }}
          />
          {preview && (
            <div className="rounded-xl border border-violet-200 bg-violet-50 p-3 text-sm">
              <div className="font-semibold text-slate-900">{preview.name}</div>
              <div className="text-slate-600">
                Run by {preview.ownerName} · {preview.playerCount} player{preview.playerCount === 1 ? "" : "s"}
              </div>
              {preview.description && <p className="mt-1 text-slate-600">{preview.description}</p>}
              <p className="mt-2 font-medium text-violet-800">
                {preview.alreadyMember
                  ? preview.joinsAs === "STORYTELLER" && preview.currentRole === "PLAYER"
                    ? "You are a player here; this code makes you a co-storyteller."
                    : "You are already in this campaign."
                  : `You will join as a ${preview.joinsAs === "STORYTELLER" ? "co-storyteller" : "player"}.`}
              </p>
              {!preview.joinEnabled && !preview.alreadyMember && (
                <p className="mt-1 text-amber-700">This campaign is not taking new players right now.</p>
              )}
            </div>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          <TextField label="Name" value={name} onChange={setName} placeholder="The Shattered Coast" />
          <TextField label="Description (optional)" value={description} onChange={setDescription} multiline />
          <p className="text-xs text-slate-500">You will be the storyteller. Invite players with the code in the campaign's settings.</p>
        </div>
      )}
      {error && <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
    </Modal>
  );
}

export default CampaignsSection;
