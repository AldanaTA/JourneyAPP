import { useEffect, useState } from "react";
import { Plus } from "lucide-react";
import type { ExpAward } from "@/apis/characterApi";
import { addExpAward, listExpAwards } from "@/apis/characterApi";
import { cn } from "@/lib/utils";
import { useDraftStore } from "./draftStore";
import { useBudget, useDraft, useReadOnly, useUpdate } from "./hooks";
import { signed } from "./rules";
import { Modal, PrimaryButton, Stepper, inputClass } from "./ui";

/**
 * The character's EXP: what it started with (some tables start higher), the EXP gained in play, and a way to add
 * more. Awards are saved at once; a negative award corrects a mistake. The starting EXP is saved with the
 * character (Save).
 */
export default function ExpModal({ onClose }: { onClose: () => void }) {
  const d = useDraft();
  const b = useBudget();
  const readOnly = useReadOnly();
  const update = useUpdate();
  // A character not saved yet has no awards to load.
  const [awards, setAwards] = useState<ExpAward[] | null>(d.id ? null : []);
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!d.id) return;
    listExpAwards(d.id).then(setAwards).catch(() => setAwards([]));
  }, [d.id]);

  const n = Number.parseInt(amount, 10);
  const add = async () => {
    if (!d.id || !n) return;
    setBusy(true);
    setError(null);
    try {
      const character = await addExpAward(d.id, n, reason.trim());
      // An award is already saved: take the new total without marking the sheet as changed.
      useDraftStore.getState().patchQuietly((x) => void (x.awardedExp = character.budget.totalExp - character.startingExp));
      setAwards(await listExpAwards(d.id));
      setAmount("");
      setReason("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not add the EXP");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title="EXP" subtitle="Starting EXP, EXP gained in play, and what is left to spend" onClose={onClose} wide>
      <div className="grid grid-cols-3 gap-2 text-center">
        {[
          ["Total", b.totalExp, "text-slate-900"],
          ["Spent", b.expSpent, "text-slate-600"],
          ["Left", b.expRemaining, b.expRemaining < 0 ? "text-red-600" : "text-violet-700"],
        ].map(([label, value, tone]) => (
          <div key={label as string} className="rounded-xl bg-slate-50 px-2 py-2">
            <div className="text-[11px] font-bold uppercase tracking-wide text-slate-500">{label}</div>
            <div className={cn("text-2xl font-bold tabular-nums", tone as string)}>{value}</div>
          </div>
        ))}
      </div>
      <p className="mt-1 text-center text-xs text-slate-500">
        {d.startingExp} starting {d.awardedExp ? `${signed(d.awardedExp)} gained` : ""}
      </p>

      {!readOnly && (
        <div className="mt-4 space-y-4">
          <div className="rounded-xl border border-slate-200 p-3">
            <Stepper
              label="Starting EXP"
              hint="150 by the rules; your storyteller may start you higher. Saved with the character."
              value={d.startingExp}
              min={0}
              step={5}
              onChange={(v) => update((x) => void (x.startingExp = v))}
            />
          </div>

          <div className="space-y-2 rounded-xl border border-slate-200 p-3">
            <div className="text-sm font-semibold text-slate-800">Add EXP</div>
            {!d.id ? (
              <p className="text-sm text-slate-500">Save the character first, then add the EXP it gains.</p>
            ) : (
              <form
                className="flex flex-wrap gap-2"
                onSubmit={(event) => {
                  event.preventDefault();
                  void add();
                }}
              >
                <input
                  value={amount}
                  onChange={(e) => setAmount(e.target.value.replace(/[^\d-]/g, "").replace(/(?!^)-/g, ""))}
                  inputMode="text"
                  pattern="-?[0-9]*"
                  placeholder="EXP"
                  aria-label="EXP gained"
                  className={cn(inputClass, "w-24 text-center font-semibold")}
                />
                <input
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="For what? (quest, training, storyteller)"
                  aria-label="Reason"
                  maxLength={500}
                  className={cn(inputClass, "min-w-0 flex-1")}
                />
                <PrimaryButton type="submit" disabled={busy || !n}>
                  <Plus size={15} /> Add
                </PrimaryButton>
              </form>
            )}
            <p className="text-xs text-slate-500">Use a negative number to take back EXP added by mistake.</p>
            {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
          </div>
        </div>
      )}

      <div className="mt-4">
        <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Gained</div>
        {awards === null ? (
          <p className="text-sm text-slate-500">Loading…</p>
        ) : awards.length === 0 ? (
          <p className="text-sm text-slate-500">No EXP gained yet.</p>
        ) : (
          <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
            {[...awards].reverse().map((a) => (
              <li key={a.id} className="flex items-center gap-3 px-3 py-2 text-sm">
                <span className={cn("w-14 shrink-0 font-bold tabular-nums", a.amount < 0 ? "text-red-600" : "text-emerald-700")}>{signed(a.amount)}</span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-slate-900">{a.reason || "No reason given"}</span>
                  <span className="block text-xs text-slate-500">
                    {new Date(a.createdAt).toLocaleDateString()}
                    {a.awardedByName ? ` · by ${a.awardedByName}` : ""}
                  </span>
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  );
}
