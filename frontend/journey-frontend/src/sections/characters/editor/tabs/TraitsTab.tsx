import { lazy, Suspense, useState } from "react";
import { getTrait } from "@/apis/characterApi";
import type { TraitSource } from "@/models/Character";
import type { EditableField } from "../abilities";
import { AbilityRow, DetailText, EditAbilityModal, LevelModal } from "../abilities";
import { applyEdits } from "../applyEdits";
import type { DraftTrait, TraitFields } from "../draft";
import { draftTrait, traitFields } from "../draft";
import { useDraftStore } from "../draftStore";
import { useBudget, useDraft, useReadOnly, useStats, useUpdate } from "../hooks";
import { usePlay } from "../playContext";
import { MAX_TRAIT_LEVEL, effectiveTrait, spendCapacities, traitLevelCost } from "../rules";
import { ConfirmDialog, EmptyNote, Fab } from "../ui";

const TraitPicker = lazy(() => import("../pickers/TraitPicker"));

const SOURCE_LABELS: Record<TraitSource, string> = { RACIAL: "Racial", JOB: "Job", PURCHASED: "Purchased" };

const TRAIT_EDIT_FIELDS: EditableField[] = [
  { key: "traitName", label: "Name", kind: "text" },
  { key: "traitDesc", label: "Description", kind: "longtext" },
  { key: "traitEffect", label: "Effect", kind: "longtext" },
  { key: "lvlUpEffect", label: "Level-up effect", kind: "longtext" },
  { key: "traitUses", label: "Uses", kind: "number" },
  { key: "mpCost", label: "MP cost", kind: "number" },
  { key: "epCost", label: "EP cost", kind: "number" },
  { key: "tpCost", label: "TP cost", kind: "number" },
  { key: "purchaseCost", label: "Purchase cost (EXP)", kind: "number" },
  { key: "lvlUpCost", label: "Level-up cost (EXP)", kind: "number" },
];

/** "Uses: 2" or "Resource cost: 10 MP · 1 TP". */
function traitSummary(f: TraitFields): string {
  const costs = [f.mpCost ? `${f.mpCost} MP` : null, f.epCost ? `${f.epCost} EP` : null, f.tpCost ? `${f.tpCost} TP` : null].filter(Boolean);
  if (costs.length) return `Resource cost: ${costs.join(" · ")}`;
  if (f.traitUses) return `Uses: ${f.traitUses}`;
  return "Uses: unlimited";
}

type Dialog =
  | { kind: "level"; traitId: string; direction: "up" | "down" }
  | { kind: "edit"; traitId: string }
  | { kind: "delete"; traitId: string }
  | { kind: "buy" }
  | null;

/** Every trait the character has, by name; purchased with the + button. */
export default function TraitsTab() {
  const d = useDraft();
  const readOnly = useReadOnly();
  const update = useUpdate();
  const b = useBudget();
  const [dialog, setDialog] = useState<Dialog>(null);
  const play = usePlay();
  const s = useStats();

  /** In play: pay the trait's MP / EP and tell the table. */
  const spend = (t: DraftTrait) => {
    const f = effectiveTrait(t);
    const costs = { MP: f.mpCost, EP: f.epCost };
    const short = spendCapacities(structuredClone(d), s, costs);
    if (short) {
      play?.notify(short, "warn");
      return;
    }
    update((x) => void spendCapacities(x, s, costs));
    const paid = [f.mpCost ? `${f.mpCost} MP` : null, f.epCost ? `${f.epCost} EP` : null, f.tpCost ? `${f.tpCost} TP` : null].filter(Boolean).join(", ");
    play?.announce(`uses ${f.traitName}${paid ? ` (${paid})` : ""}`);
    play?.notify(`${f.traitName}${paid ? `: ${paid}` : ""}`);
  };

  /** In play: mark one use of the trait. */
  const use = (t: DraftTrait) => {
    const f = effectiveTrait(t);
    const total = f.traitUses ?? 0;
    if (t.usesSpent >= total) {
      play?.notify(`No uses of ${f.traitName} left until you rest`, "warn");
      return;
    }
    change(t.traitId, (row) => void (row.usesSpent += 1));
    const left = total - t.usesSpent - 1;
    play?.announce(`uses ${f.traitName} (${left} use${left === 1 ? "" : "s"} left)`);
    play?.notify(`${f.traitName}: ${left} of ${total} uses left`);
  };
  const anyUsed = d.traits.some((t) => t.usesSpent > 0);

  const traits = [...d.traits].sort((x, y) => effectiveTrait(x).traitName.localeCompare(effectiveTrait(y).traitName));
  const find = (id: string) => d.traits.find((t) => t.traitId === id);
  const change = (id: string, recipe: (t: DraftTrait) => void) =>
    update((x) => {
      const t = x.traits.find((row) => row.traitId === id);
      if (t) recipe(t);
    });

  const openEdit = (t: DraftTrait) => {
    setDialog({ kind: "edit", traitId: t.traitId });
    if (!t.baseKnown) {
      getTrait(t.traitId)
        .then((trait) =>
          useDraftStore.getState().patchQuietly((x) => {
            const row = x.traits.find((r) => r.traitId === t.traitId);
            if (row) {
              row.base = traitFields(trait);
              row.baseKnown = true;
            }
          }),
        )
        .catch(() => undefined);
    }
  };

  const current = dialog && "traitId" in dialog ? find(dialog.traitId) : undefined;

  return (
    <div className="space-y-3">
      {play && anyUsed && (
        <div className="flex items-center justify-between rounded-xl bg-violet-50 px-3 py-2 text-sm">
          <span className="text-violet-900">Uses come back when you rest.</span>
          <button type="button" onClick={() => {
            update((x) => x.traits.forEach((t) => void (t.usesSpent = 0)));
            play.announce("rests: trait uses are back");
            play.notify("Trait uses reset");
          }} className="rounded-lg bg-white px-2.5 py-1 text-xs font-semibold text-violet-700 shadow-sm">
            Rest
          </button>
        </div>
      )}
      {traits.length === 0 ? (
        <EmptyNote>No traits yet. {readOnly ? "" : "Racial and job traits are picked on the Identity and Job tabs; buy more with +."}</EmptyNote>
      ) : (
        <ul className="space-y-2">
          {traits.map((t) => {
            const f = effectiveTrait(t);
            const cost = traitLevelCost(t);
            return (
              <AbilityRow
                key={t.traitId}
                title={f.traitName}
                summary={traitSummary(f)}
                badges={
                  <>
                    <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] font-semibold text-slate-600">LVL {t.lvl}</span>
                    <span className="rounded bg-violet-50 px-1.5 py-0.5 text-[11px] font-medium text-violet-700">{SOURCE_LABELS[t.source]}</span>
                    {Object.keys(t.edits).length > 0 && (
                      <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[11px] font-medium text-amber-800">Customized</span>
                    )}
                  </>
                }
                readOnly={readOnly}
                onDelete={() => setDialog({ kind: "delete", traitId: t.traitId })}
                onEdit={() => openEdit(t)}
                onLevelUp={() => setDialog({ kind: "level", traitId: t.traitId, direction: "up" })}
                onLevelDown={() => setDialog({ kind: "level", traitId: t.traitId, direction: "down" })}
                actions={
                  play && (
                    <>
                      {(f.mpCost || f.epCost) ? (
                        <button type="button" onClick={() => spend(t)} title="Spends the cost from your capacities"
                          className="rounded-lg bg-violet-600 px-2.5 py-1 text-xs font-semibold text-white shadow-sm hover:bg-violet-700">
                          Spend {[f.mpCost ? `${f.mpCost} MP` : null, f.epCost ? `${f.epCost} EP` : null].filter(Boolean).join(" ")}
                        </button>
                      ) : null}
                      {f.traitUses ? (
                        <button type="button" onClick={() => use(t)} disabled={t.usesSpent >= f.traitUses} title="Spend one use"
                          className="rounded-lg border border-violet-300 bg-white px-2.5 py-1 text-xs font-semibold text-violet-700 hover:bg-violet-50 disabled:opacity-40">
                          Use · {Math.max(0, f.traitUses - t.usesSpent)}/{f.traitUses}
                        </button>
                      ) : null}
                    </>
                  )
                }
                levelUpDisabled={t.lvl >= MAX_TRAIT_LEVEL ? `Traits stop at LVL ${MAX_TRAIT_LEVEL}` : cost > b.expRemaining ? `Needs ${cost} EXP` : null}
                levelDownDisabled={t.lvl <= 1 ? "Already LVL 1" : null}
              >
                <DetailText label="Description" text={f.traitDesc} />
                <DetailText label="Effect" text={f.traitEffect} />
                <DetailText label="Level-up effect" text={f.lvlUpEffect} />
                <p className="text-xs text-slate-500">
                  {t.source === "JOB" ? `${f.purchaseCost ?? 0} job points` : t.source === "RACIAL" ? "From race" : `${f.purchaseCost ?? 0} EXP`}
                  {" · "}
                  {t.expSpent} EXP spent · next level {cost} EXP
                </p>
                <DetailText label="Notes" text={t.notes} />
              </AbilityRow>
            );
          })}
        </ul>
      )}

      {!readOnly && <Fab label="Buy a trait" onClick={() => setDialog({ kind: "buy" })} className="bottom-24" />}

      {dialog?.kind === "level" && current && (
        <LevelModal
          name={effectiveTrait(current).traitName}
          direction={dialog.direction}
          fromLevel={current.lvl}
          cost={traitLevelCost(current)}
          expRemaining={b.expRemaining}
          effect={effectiveTrait(current).traitEffect ?? ""}
          lvlUpEffect={effectiveTrait(current).lvlUpEffect}
          onClose={() => setDialog(null)}
          onConfirm={(effect) => {
            const cost = traitLevelCost(current);
            change(current.traitId, (t) => {
              if (dialog.direction === "up") {
                t.lvl += 1;
                t.expSpent += cost;
              } else {
                t.lvl -= 1;
                t.expSpent = Math.max(0, t.expSpent - cost);
              }
              if (effect !== (effectiveTrait(t).traitEffect ?? "")) {
                if (t.baseKnown && effect === (t.base.traitEffect ?? "")) delete t.edits.traitEffect;
                else t.edits.traitEffect = effect;
              }
            });
            setDialog(null);
          }}
        />
      )}

      {dialog?.kind === "edit" && current && (
        <EditAbilityModal<TraitFields>
          name={effectiveTrait(current).traitName}
          notes={current.notes}
          fields={TRAIT_EDIT_FIELDS}
          values={effectiveTrait(current)}
          compendium={current.baseKnown ? current.base : null}
          edited={new Set(Object.keys(current.edits))}
          onClose={() => setDialog(null)}
          onSave={(notes, values, reset) =>
            change(current.traitId, (t) => {
              t.notes = notes;
              t.edits = applyEdits(t.base, t.baseKnown, t.edits, effectiveTrait(t), values, reset);
            })
          }
        />
      )}

      {dialog?.kind === "delete" && current && (
        <ConfirmDialog
          title={`Remove ${effectiveTrait(current).traitName}?`}
          message={current.expSpent > 0 ? `The ${current.expSpent} EXP spent on it is refunded.` : "It is removed from the character."}
          confirmLabel="Remove"
          onClose={() => setDialog(null)}
          onConfirm={() => {
            update((x) => void (x.traits = x.traits.filter((t) => t.traitId !== current.traitId)));
            setDialog(null);
          }}
        />
      )}

      <Suspense fallback={null}>
        {dialog?.kind === "buy" && (
          <TraitPicker
            expRemaining={b.expRemaining}
            ownedIds={new Set(d.traits.map((t) => t.traitId))}
            onPick={(trait) => update((x) => void x.traits.push(draftTrait(trait, "PURCHASED", null, trait.purchaseCost ?? 0)))}
            onClose={() => setDialog(null)}
          />
        )}
      </Suspense>
    </div>
  );
}
