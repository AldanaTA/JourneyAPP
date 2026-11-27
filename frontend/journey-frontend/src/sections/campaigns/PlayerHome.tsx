import { useEffect, useState } from "react";
import { AlertTriangle, Download, ExternalLink, UserPlus } from "lucide-react";
import { useNavigate } from "react-router-dom";
import type { CampaignCharacter, ImportCheck } from "@/apis/campaignApi";
import { checkImport, importCharacter, listCampaignCharacters } from "@/apis/campaignApi";
import { listCharacters } from "@/apis/characterApi";
import { useAuthStore } from "@/auth/authStore";
import type { CharacterSummary } from "@/models/Character";
import { Modal, Panel, PrimaryButton, SecondaryButton } from "../characters/editor/ui";
import { returnParam } from "../characters/editor/returnTo";
import { useCampaign } from "./campaignStore";

const KIND_LABELS: Record<string, string> = {
  RACE: "Race", JOB: "Job", SHEET: "Sheet", TRAIT: "Trait", POWER: "Power", ITEM: "Item", SOURCE: "Source",
};

/** A player's view of a campaign: their characters here, bring one in, or create a new one. */
export default function PlayerHome() {
  const campaign = useCampaign();
  const navigate = useNavigate();
  const me = useAuthStore((state) => state.user?.id);
  const [mine, setMine] = useState<CampaignCharacter[] | null>(null);
  const [picking, setPicking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = () =>
    listCampaignCharacters(campaign.id)
      .then((list) => setMine(list.filter((c) => c.playerId === me)))
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Could not load your characters"));
  useEffect(() => {
    void reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [campaign.id, me]);

  return (
    <main className="flex-1 overflow-y-auto px-4 py-4">
      <div className="mx-auto max-w-2xl space-y-4">
        {campaign.description && <p className="text-sm text-slate-600">{campaign.description}</p>}
        <div className="grid gap-3 sm:grid-cols-2">
          <button type="button" onClick={() => setPicking(true)} className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-4 text-left shadow-sm hover:border-violet-300">
            <Download className="text-violet-600" />
            <span>
              <span className="block font-semibold text-slate-900">Import character</span>
              <span className="block text-sm text-slate-500">Bring one of your characters in as a campaign version.</span>
            </span>
          </button>
          <button type="button" onClick={() => navigate(`/characters/new/edit${returnParam(`/campaigns/${campaign.id}`)}`)} className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-4 text-left shadow-sm hover:border-violet-300">
            <UserPlus className="text-violet-600" />
            <span>
              <span className="block font-semibold text-slate-900">Create character</span>
              <span className="block text-sm text-slate-500">Make a new one, then import it here.</span>
            </span>
          </button>
        </div>
        <Panel title="Your characters in this campaign">
          {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
          {mine?.length === 0 && <p className="text-sm text-slate-500">None yet.</p>}
          <ul className="space-y-1.5">
            {mine?.map((c) => (
              <li key={c.id}>
                <button type="button" onClick={() => navigate(`/characters/${c.id}/edit${returnParam(`/campaigns/${campaign.id}`)}`)} className="flex w-full items-center gap-2 rounded-xl border border-slate-200 px-3 py-2 text-left hover:border-violet-300">
                  <span className="min-w-0 flex-1">
                    <span className="block font-medium text-slate-900">{c.name}</span>
                    <span className="block text-xs text-slate-500">
                      {[c.raceNames.join(" / "), c.jobName].filter(Boolean).join(" · ") || "Not finished"}
                      {c.status === "DRAFT" ? " · draft" : ""}
                      {c.disallowedContent > 0 ? ` · ${c.disallowedContent} things the campaign no longer allows` : ""}
                    </span>
                  </span>
                  <ExternalLink size={14} className="text-slate-400" />
                </button>
              </li>
            ))}
          </ul>
        </Panel>
      </div>
      {picking && <ImportModal onClose={() => setPicking(false)} onImported={() => { setPicking(false); void reload(); }} />}
    </main>
  );
}

/** Pick a character, see what the campaign would remove from its campaign version, and confirm. */
function ImportModal({ onClose, onImported }: { onClose: () => void; onImported: () => void }) {
  const campaign = useCampaign();
  const [characters, setCharacters] = useState<CharacterSummary[] | null>(null);
  const [check, setCheck] = useState<ImportCheck | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listCharacters().then(setCharacters).catch(() => setCharacters([]));
  }, []);

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

  return (
    <Modal
      title={check ? `Import ${check.characterName}?` : "Import a character"}
      onClose={onClose}
      wide
      footer={
        check ? (
          <>
            <SecondaryButton onClick={() => setCheck(null)}>Pick another</SecondaryButton>
            <PrimaryButton disabled={busy || !check.canImport} onClick={() => run(async () => { await importCharacter(campaign.id, check.characterId); onImported(); })}>
              {check.removals.length ? "Import and remove these" : "Import"}
            </PrimaryButton>
          </>
        ) : (
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
        )
      }
    >
      {!check ? (
        <ul className="space-y-1.5">
          {characters === null && <li className="text-sm text-slate-500">Loading…</li>}
          {characters?.length === 0 && <li className="text-sm text-slate-500">You have no characters yet. Create one first.</li>}
          {characters?.map((c) => (
            <li key={c.id}>
              <button type="button" disabled={busy} onClick={() => run(async () => setCheck(await checkImport(campaign.id, c.id)))}
                className="flex w-full items-center justify-between rounded-xl border border-slate-200 px-3 py-2 text-left hover:border-violet-300">
                <span>
                  <span className="block font-medium text-slate-900">{c.name}</span>
                  <span className="block text-xs text-slate-500">{[c.raceNames.join(" / "), c.jobName].filter(Boolean).join(" · ") || "Not finished"}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      ) : !check.canImport ? (
        <p className="text-sm text-slate-700">{check.message}</p>
      ) : check.removals.length === 0 ? (
        <p className="text-sm text-slate-700">{check.message}. The campaign version is a copy: your original character does not change.</p>
      ) : (
        <div className="space-y-3">
          <div className="flex gap-2 rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
            <AlertTriangle size={18} className="shrink-0" />
            <p>{check.message}. You can pick another character or create a new one instead.</p>
          </div>
          <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
            {check.removals.map((r) => (
              <li key={`${r.kind}:${r.id}`} className="flex items-center gap-2 px-3 py-2 text-sm">
                <span className="w-14 shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-center text-[10px] font-semibold uppercase text-slate-600">{KIND_LABELS[r.kind]}</span>
                <span className="min-w-0 flex-1">
                  <span className="block font-medium text-slate-900">{r.name}{r.quantity > 1 ? ` ×${r.quantity}` : ""}</span>
                  <span className="block text-xs text-slate-500">
                    {r.reason === "RACE_REMOVED" ? "Comes with a removed race" : r.reason === "JOB_REMOVED" ? "Comes with a removed job" : `From ${r.source}`}
                  </span>
                </span>
                {r.expSpent > 0 && <span className="text-xs font-semibold text-emerald-700">+{r.expSpent} EXP back</span>}
              </li>
            ))}
          </ul>
          <p className="text-xs text-slate-500">
            {check.refundedExp > 0 ? `${check.refundedExp} EXP comes back to spend. ` : ""}
            {check.becomesDraft ? "The campaign version becomes a draft until you choose again. " : ""}
            Your original character keeps everything.
          </p>
        </div>
      )}
      {error && <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
    </Modal>
  );
}
