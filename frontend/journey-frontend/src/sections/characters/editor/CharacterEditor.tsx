import { lazy, Suspense, useEffect, useState } from "react";
import { createPortal } from "react-dom";
import type { LucideIcon } from "lucide-react";
import { Backpack, Briefcase, CircleAlert, Dumbbell, Pencil, Save, Sparkles, Target, UserRound, Wand2, X } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { getCharacter, getCharacterInventory, getJob, getRace } from "@/apis/characterApi";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import { draftFromServer, newDraft } from "./draft";
import { useDraftStore, useIsDirty } from "./draftStore";
import { useBudget } from "./hooks";
import { useReferenceStore } from "./referenceStore";
import SourcesPage from "./SourcesPage";
import { Modal, PrimaryButton, SecondaryButton } from "./ui";

// Each tab is downloaded the first time it is opened.
const IdentityTab = lazy(() => import("./tabs/IdentityTab"));
const AttributesTab = lazy(() => import("./tabs/AttributesTab"));
const JobTab = lazy(() => import("./tabs/JobTab"));
const TraitsTab = lazy(() => import("./tabs/TraitsTab"));
const PowersTab = lazy(() => import("./tabs/PowersTab"));
const SkillsTab = lazy(() => import("./tabs/SkillsTab"));
const InventoryTab = lazy(() => import("./tabs/InventoryTab"));

type TabId = "identity" | "attributes" | "job" | "traits" | "powers" | "skills" | "inventory";

const TABS: { id: TabId; label: string; icon: LucideIcon }[] = [
  { id: "identity", label: "Identity", icon: UserRound },
  { id: "attributes", label: "Attributes", icon: Dumbbell },
  { id: "job", label: "Job", icon: Briefcase },
  { id: "traits", label: "Traits", icon: Sparkles },
  { id: "powers", label: "Powers", icon: Wand2 },
  { id: "skills", label: "Skills", icon: Target },
  { id: "inventory", label: "Inventory", icon: Backpack },
];

/** How often unsaved changes are saved on their own. */
const AUTOSAVE_MS = 30_000;

/**
 * Creates, edits, or views a character in a full-screen window. Routes: /characters/new/edit (create),
 * /characters/:id/edit, and /characters/:id (view, read-only). Changes stay in the browser until Save or the
 * periodic autosave, which send the whole character in one request.
 */
export default function CharacterEditor({ mode }: { mode: "edit" | "view" }) {
  const { characterId = "new" } = useParams();
  const navigate = useNavigate();
  const isNew = characterId === "new";
  const readOnly = mode === "view";

  const referenceStatus = useReferenceStore((state) => state.status);
  const referenceError = useReferenceStore((state) => state.error);
  const loadReference = useReferenceStore((state) => state.load);
  const draft = useDraftStore((state) => state.draft);
  const draftId = draft?.id ?? null;
  const open = useDraftStore((state) => state.open);
  const close = useDraftStore((state) => state.close);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>("identity");

  useEffect(() => {
    void loadReference();
  }, [loadReference]);

  // Load the character (unless it is the one already open, e.g. just created by the first save).
  useEffect(() => {
    if (isNew) return;
    const current = useDraftStore.getState();
    if (current.draft?.id === characterId && current.readOnly === readOnly) return;
    const controller = new AbortController();
    Promise.all([getCharacter(characterId, controller.signal), getCharacterInventory(characterId, controller.signal)])
      .then(([character, inventory]) => {
        open(draftFromServer(character, inventory), readOnly, true);
        setLoadError(null);
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setLoadError(error instanceof Error ? error.message : "Could not load the character");
      });
    return () => controller.abort();
  }, [characterId, isNew, readOnly, open]);

  // Fetch the trait lists of the character's races and job (needed to pick racial and job traits).
  const missingTraitLists = draft
    ? [...draft.races.filter((r) => r.traits === null).map((r) => `race:${r.id}`), ...(draft.job && draft.job.traits === null ? [`job:${draft.job.id}`] : [])].join(",")
    : "";
  useEffect(() => {
    if (!missingTraitLists) return;
    for (const key of missingTraitLists.split(",")) {
      const [kind, id] = key.split(":");
      const fetched = kind === "race" ? getRace(id).then((race) => race.traits) : getJob(id).then((job) => job.traits);
      fetched
        .then((traits) =>
          useDraftStore.getState().patchQuietly((d) => {
            if (kind === "race") {
              const race = d.races.find((r) => r.id === id);
              if (race) race.traits = traits;
            } else if (d.job?.id === id) {
              d.job.traits = traits;
            }
          }),
        )
        .catch(() => undefined);
    }
  }, [missingTraitLists]);

  // Once the first save has created the character, move to its own address (the editor stays open).
  useEffect(() => {
    if (isNew && draftId) navigate(`/characters/${draftId}/edit`, { replace: true });
  }, [isNew, draftId, navigate]);

  // Close the character when leaving the editor.
  useEffect(() => () => close(), [close]);

  if (referenceStatus === "error" || loadError) {
    return (
      <EditorFrame>
        <div className="m-auto max-w-sm space-y-3 p-6 text-center">
          <p className="text-sm text-red-700">{loadError ?? referenceError}</p>
          <SecondaryButton onClick={() => navigate("/characters")}>Back to characters</SecondaryButton>
        </div>
      </EditorFrame>
    );
  }
  if (referenceStatus !== "ready" || (!isNew && !draft)) {
    return (
      <EditorFrame>
        <div className="m-auto flex items-center gap-2 text-slate-600">
          <Spinner className="h-5 w-5" /> Loading…
        </div>
      </EditorFrame>
    );
  }
  if (isNew && !draft) {
    return (
      <EditorFrame>
        <SourcesPage
          initial={[]}
          onCancel={() => navigate("/characters")}
          onContinue={(sourceIds) => open(newDraft(sourceIds), false, false)}
        />
      </EditorFrame>
    );
  }

  const ActiveTab = {
    identity: IdentityTab,
    attributes: AttributesTab,
    job: JobTab,
    traits: TraitsTab,
    powers: PowersTab,
    skills: SkillsTab,
    inventory: InventoryTab,
  }[tab];

  return (
    <EditorFrame>
      <EditorHeader readOnly={readOnly} showJobPoints={tab === "job"} />
      <main className="flex-1 overflow-y-auto overscroll-contain px-4 pb-28 pt-4">
        <div className="mx-auto max-w-3xl">
          <Suspense
            fallback={
              <div className="flex justify-center py-10 text-slate-500">
                <Spinner className="h-5 w-5" />
              </div>
            }
          >
            <ActiveTab />
          </Suspense>
        </div>
      </main>
      <nav
        aria-label="Character sections"
        className="absolute inset-x-0 bottom-0 z-20 border-t border-white/10 bg-[#201a12] px-1 pb-[max(0.25rem,env(safe-area-inset-bottom))] pt-1"
      >
        <div className="mx-auto flex max-w-3xl justify-between">
          {TABS.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              type="button"
              onClick={() => setTab(id)}
              aria-current={tab === id ? "page" : undefined}
              className={cn(
                "flex min-w-0 flex-1 flex-col items-center gap-0.5 rounded-xl px-0.5 py-1.5 text-[10px] font-semibold tracking-wide transition",
                tab === id ? "bg-violet-500 text-white shadow shadow-violet-500/30" : "text-white/70 hover:bg-white/10 hover:text-white",
              )}
            >
              <Icon size={18} />
              <span className="max-w-full truncate">{label}</span>
            </button>
          ))}
        </div>
      </nav>
    </EditorFrame>
  );
}

/** The full-screen window the editor lives in. */
function EditorFrame({ children }: { children: React.ReactNode }) {
  // Keep the page behind from scrolling while the window is open.
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);
  // Portaled to <body>: the app layout's backdrop blur would otherwise trap this fixed window inside it.
  return createPortal(
    <div className="fixed inset-0 z-40 flex flex-col bg-[#f5efe7] text-slate-900">{children}</div>,
    document.body,
  );
}

/** X, the EXP left, and Save; saves on its own every 30 seconds while there are unsaved changes. */
function EditorHeader({ readOnly, showJobPoints }: { readOnly: boolean; showJobPoints: boolean }) {
  const navigate = useNavigate();
  const name = useDraftStore((state) => state.draft?.name);
  const id = useDraftStore((state) => state.draft?.id);
  const saving = useDraftStore((state) => state.saving);
  const saveError = useDraftStore((state) => state.saveError);
  const save = useDraftStore((state) => state.save);
  const saveErrorSeen = useDraftStore((state) => state.saveErrorSeen);
  const setSaveErrorSeen = useDraftStore((state) => state.setSaveErrorSeen);
  const dirty = useIsDirty();
  const b = useBudget();
  const [confirmClose, setConfirmClose] = useState(false);

  useEffect(() => {
    if (readOnly) return;
    const timer = window.setInterval(() => {
      const state = useDraftStore.getState();
      const isDirty = state.version !== state.savedVersion;
      if (isDirty && !state.saving && state.failedVersion !== state.version) void state.save();
    }, AUTOSAVE_MS);
    return () => window.clearInterval(timer);
  }, [readOnly]);

  // Warn before the tab is closed with unsaved changes.
  useEffect(() => {
    if (!dirty || readOnly) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty, readOnly]);

  const leave = () => navigate("/characters");
  const status = saving ? "Saving…" : saveError ? "Not saved" : dirty ? "Unsaved" : "Saved";

  return (
    <header className="z-20 flex items-center gap-2 bg-[#201a12] px-3 py-2.5 text-white">
      <button
        type="button"
        onClick={() => (dirty && !readOnly ? setConfirmClose(true) : leave())}
        aria-label="Close"
        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-white/10 bg-white/5 hover:bg-white/10"
      >
        <X size={20} />
      </button>
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-semibold">{name?.trim() || "New character"}</div>
        {!readOnly &&
          (saveError && !saving ? (
            <button type="button" onClick={() => setSaveErrorSeen(false)} className="block text-[11px] font-semibold text-red-300 underline">
              Not saved · see why
            </button>
          ) : (
            <div className="truncate text-[11px] text-white/60">{status}</div>
          ))}
      </div>
      <div className="flex shrink-0 items-center gap-1.5">
        {showJobPoints && (
          <span className={cn("rounded-lg px-2 py-1 text-xs font-semibold tabular-nums", b.jobPointsRemaining < 0 ? "bg-red-500/80" : "bg-white/10")}>
            {b.jobPointsRemaining} JP
          </span>
        )}
        <span
          className={cn("rounded-lg px-2 py-1 text-xs font-semibold tabular-nums", b.expRemaining < 0 ? "bg-red-500/80" : "bg-violet-500/90")}
          title={`${b.expSpent} of ${b.totalExp} EXP spent`}
        >
          {b.expRemaining} EXP left
        </span>
        {readOnly ? (
          id && (
            <button
              type="button"
              onClick={() => navigate(`/characters/${id}/edit`)}
              className="flex h-9 items-center gap-1.5 rounded-xl bg-white/10 px-3 text-sm font-semibold hover:bg-white/20"
            >
              <Pencil size={15} /> Edit
            </button>
          )
        ) : (
          <button
            type="button"
            onClick={() => void save()}
            disabled={saving || !dirty}
            className="flex h-9 items-center gap-1.5 rounded-xl bg-white px-3 text-sm font-semibold text-[#201a12] transition hover:bg-violet-50 disabled:opacity-60"
          >
            {saving ? <Spinner className="h-4 w-4" /> : <Save size={15} />} Save
          </button>
        )}
      </div>

      {saveError && !saveErrorSeen && !confirmClose && (
        <Modal
          title="Could not save"
          onClose={() => setSaveErrorSeen(true)}
          footer={<PrimaryButton onClick={() => setSaveErrorSeen(true)}>OK</PrimaryButton>}
        >
          <div className="flex gap-3">
            <CircleAlert size={20} className="mt-0.5 shrink-0 text-red-600" />
            <div className="space-y-2 text-sm text-slate-700">
              <p className="font-medium text-slate-900">{saveError}</p>
              <p>Your changes are still here. Fix the problem and save again; autosave tries again after your next change.</p>
            </div>
          </div>
        </Modal>
      )}

      {confirmClose && (
        <Modal
          title="Unsaved changes"
          onClose={() => setConfirmClose(false)}
          footer={
            <>
              <SecondaryButton onClick={leave}>Discard</SecondaryButton>
              <PrimaryButton
                disabled={saving}
                onClick={async () => {
                  if (await save()) leave();
                  else setConfirmClose(false);
                }}
              >
                Save and close
              </PrimaryButton>
            </>
          }
        >
          <p className="text-sm text-slate-600">This character has changes that are not saved yet.</p>
        </Modal>
      )}
    </header>
  );
}
