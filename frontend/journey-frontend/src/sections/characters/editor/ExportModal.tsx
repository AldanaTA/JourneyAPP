import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2 } from "lucide-react";
import { useNavigate } from "react-router-dom";
import type { ImportCheck } from "@/apis/campaignApi";
import { checkExport, exportCharacter } from "@/apis/characterApi";
import { Spinner } from "@/components/ui/spinner";
import { useDraftStore } from "./draftStore";
import { Modal, PrimaryButton, SecondaryButton } from "./ui";

const KIND_LABELS: Record<string, string> = {
  RACE: "Race", JOB: "Job", SHEET: "Sheet", TRAIT: "Trait", POWER: "Power", ITEM: "Item", SOURCE: "Source",
};

/**
 * Exports the character to the player's own characters: saves first, lists what the copy leaves out (content from
 * sources the player does not own), and makes the copy once confirmed. This character is not changed.
 */
export function ExportModal({ characterId, onClose }: { characterId: string; onClose: () => void }) {
  const navigate = useNavigate();
  const [check, setCheck] = useState<ImportCheck | null>(null);
  const [done, setDone] = useState<{ id: string; name: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const state = useDraftStore.getState();
      // Export what is on screen: save unsaved changes first.
      if (state.version !== state.savedVersion && !(await state.save())) {
        throw new Error(useDraftStore.getState().saveError ?? "Save the character first");
      }
      return checkExport(characterId);
    })()
      .then((result) => !cancelled && setCheck(result))
      .catch((reason: unknown) => !cancelled && setError(reason instanceof Error ? reason.message : "Could not check the character"));
    return () => {
      cancelled = true;
    };
  }, [characterId]);

  const run = async () => {
    setBusy(true);
    setError(null);
    try {
      setDone(await exportCharacter(characterId));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not export");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title="Export to my characters"
      subtitle="Makes a copy in your Characters list, outside the campaign."
      onClose={onClose}
      wide
      footer={
        done ? (
          <>
            <SecondaryButton onClick={onClose}>Close</SecondaryButton>
            <PrimaryButton onClick={() => navigate(`/characters/${done.id}/edit`)}>Open the copy</PrimaryButton>
          </>
        ) : (
          <>
            <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
            <PrimaryButton disabled={!check || busy} onClick={() => void run()}>
              {check?.removals.length ? "Export without these" : "Export"}
            </PrimaryButton>
          </>
        )
      }
    >
      {done ? (
        <p className="flex items-center gap-2 text-sm font-medium text-emerald-800">
          <CheckCircle2 size={18} /> {done.name} is now in your characters.
        </p>
      ) : !check && !error ? (
        <div className="flex items-center justify-center gap-2 py-6 text-slate-500"><Spinner className="h-5 w-5" /> Checking…</div>
      ) : check && check.removals.length === 0 ? (
        <p className="text-sm text-slate-700">{check.message}. The copy will be just like this character.</p>
      ) : check ? (
        <div className="space-y-3">
          <div className="flex gap-2 rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
            <AlertTriangle size={18} className="shrink-0" />
            <p>{check.message}.</p>
          </div>
          <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
            {check.removals.map((r) => (
              <li key={`${r.kind}:${r.id}`} className="flex items-center gap-2 px-3 py-2 text-sm">
                <span className="w-14 shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-center text-[10px] font-semibold uppercase text-slate-600">{KIND_LABELS[r.kind]}</span>
                <span className="min-w-0 flex-1">
                  <span className="block font-medium text-slate-900">{r.name}{r.quantity > 1 ? ` ×${r.quantity}` : ""}</span>
                  <span className="block text-xs text-slate-500">
                    {r.reason === "RACE_REMOVED" ? "Comes with a left-out race" : r.reason === "JOB_REMOVED" ? "Comes with a left-out job" : `From ${r.source}`}
                  </span>
                </span>
                {r.expSpent > 0 && <span className="text-xs font-semibold text-emerald-700">+{r.expSpent} EXP back</span>}
              </li>
            ))}
          </ul>
          <p className="text-xs text-slate-500">
            {check.refundedExp > 0 ? `The copy gets ${check.refundedExp} EXP back to spend. ` : ""}
            {check.becomesDraft ? "The copy is a draft until you choose again. " : ""}
            This character keeps everything.
          </p>
        </div>
      ) : null}
      {error && <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
    </Modal>
  );
}
