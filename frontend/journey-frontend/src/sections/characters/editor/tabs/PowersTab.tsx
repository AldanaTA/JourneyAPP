import { lazy, Suspense, useState } from "react";
import { getPower } from "@/apis/characterApi";
import type { EditableField } from "../abilities";
import { AbilityRow, DetailText, EditAbilityModal, LevelModal } from "../abilities";
import { applyEdits } from "../applyEdits";
import type { DraftPower, PowerFields } from "../draft";
import { draftPower, powerFields } from "../draft";
import { useDraftStore } from "../draftStore";
import { useBudget, useDraft, useReadOnly, useStats, useUpdate } from "../hooks";
import { usePlay } from "../playContext";
import { POWER_COST, effectivePower, powerLevelCost, spendCapacities } from "../rules";
import { ConfirmDialog, EmptyNote, Fab, Panel } from "../ui";

const PowerPicker = lazy(() => import("../pickers/PowerPicker"));

const TYPES = ["Destruction", "Support", "Sabotage", "Utility"];

const POWER_EDIT_FIELDS: EditableField[] = [
  { key: "name", label: "Name", kind: "text" },
  { key: "type", label: "Type", kind: "select", options: TYPES },
  { key: "mpCost", label: "MP cost", kind: "number" },
  { key: "tpCost", label: "TP cost", kind: "number" },
  { key: "hpCost", label: "HP cost", kind: "number" },
  { key: "epCost", label: "EP cost", kind: "number" },
  { key: "range", label: "Range", kind: "text" },
  { key: "area", label: "Area", kind: "text" },
  { key: "duration", label: "Duration", kind: "text" },
  { key: "materialComponents", label: "Material components", kind: "text" },
  { key: "verbal", label: "Verbal", kind: "boolean" },
  { key: "somatic", label: "Somatic", kind: "boolean" },
  { key: "sight", label: "Sight", kind: "boolean" },
  { key: "concentration", label: "Concentration", kind: "boolean" },
  { key: "isDistinct", label: "Distinct", kind: "boolean" },
  { key: "effect", label: "Effect", kind: "longtext" },
  { key: "empowerEffect", label: "Empower", kind: "longtext" },
  { key: "lvlUpEffect", label: "Level-up effect", kind: "longtext" },
];

/** "10 MP · 2 TP". */
function powerCost(f: PowerFields): string {
  const costs = [
    f.mpCost !== null ? `${f.mpCost} MP` : null,
    f.hpCost ? `${f.hpCost} HP` : null,
    f.epCost ? `${f.epCost} EP` : null,
    f.tpCost !== null ? `${f.tpCost} TP` : null,
  ].filter(Boolean);
  return costs.join(" · ") || "None";
}

function components(f: PowerFields): string | null {
  const list = [f.verbal && "Verbal", f.somatic && "Somatic", f.sight && "Sight"].filter(Boolean);
  return list.length ? list.join(", ") : null;
}

type Dialog =
  | { kind: "level"; powerId: string; direction: "up" | "down" }
  | { kind: "edit"; powerId: string }
  | { kind: "delete"; powerId: string }
  | { kind: "learn" }
  | null;

/** The character's powers, in a section per power type; learned with the + button. */
export default function PowersTab() {
  const d = useDraft();
  const readOnly = useReadOnly();
  const update = useUpdate();
  const b = useBudget();
  const [dialog, setDialog] = useState<Dialog>(null);
  const play = usePlay();
  const s = useStats();

  /** In play: pay the power's MP / HP / EP from the character's current capacities and tell the table. */
  const cast = (p: DraftPower) => {
    const f = effectivePower(p);
    const costs = { MP: f.mpCost, HP: f.hpCost, EP: f.epCost };
    const short = spendCapacities(structuredClone(d), s, costs);
    if (short) {
      play?.notify(short, "warn");
      return;
    }
    update((x) => void spendCapacities(x, s, costs));
    const paid = [f.mpCost ? `${f.mpCost} MP` : null, f.hpCost ? `${f.hpCost} HP` : null, f.epCost ? `${f.epCost} EP` : null, f.tpCost ? `${f.tpCost} TP` : null].filter(Boolean).join(", ");
    play?.announce(`casts ${f.name}${paid ? ` (${paid})` : ""}`);
    play?.notify(`Cast ${f.name}${paid ? `: ${paid}` : ""}`);
  };

  const find = (id: string) => d.powers.find((p) => p.powerId === id);
  const change = (id: string, recipe: (p: DraftPower) => void) =>
    update((x) => {
      const p = x.powers.find((row) => row.powerId === id);
      if (p) recipe(p);
    });
  const openEdit = (p: DraftPower) => {
    setDialog({ kind: "edit", powerId: p.powerId });
    if (!p.baseKnown) {
      getPower(p.powerId)
        .then((power) =>
          useDraftStore.getState().patchQuietly((x) => {
            const row = x.powers.find((r) => r.powerId === p.powerId);
            if (row) {
              row.base = powerFields(power);
              row.baseKnown = true;
            }
          }),
        )
        .catch(() => undefined);
    }
  };

  const sorted = [...d.powers].sort((x, y) => effectivePower(x).name.localeCompare(effectivePower(y).name));
  const groups = [...TYPES, "Other"].map((type) => ({
    type,
    powers: sorted.filter((p) => {
      const t = effectivePower(p).type;
      return type === "Other" ? !t || !TYPES.includes(t) : t === type;
    }),
  }));
  const current = dialog && "powerId" in dialog ? find(dialog.powerId) : undefined;

  return (
    <div className="space-y-4">
      {d.powers.length === 0 && <EmptyNote>No powers yet.{readOnly ? "" : ` Learn one with + (${POWER_COST} EXP each).`}</EmptyNote>}

      {groups
        .filter((g) => g.powers.length > 0)
        .map((g) => (
          <Panel key={g.type} title={g.type} action={<span className="text-xs text-slate-400">{g.powers.length}</span>}>
            <ul className="space-y-2">
              {g.powers.map((p) => {
                const f = effectivePower(p);
                const cost = powerLevelCost(p.lvl);
                return (
                  <AbilityRow
                    key={p.powerId}
                    title={f.name}
                    summary={`Cost: ${powerCost(f)}`}
                    badges={
                      <>
                        <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] font-semibold text-slate-600">LVL {p.lvl}</span>
                        {Object.keys(p.edits).length > 0 && (
                          <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[11px] font-medium text-amber-800">Customized</span>
                        )}
                      </>
                    }
                    readOnly={readOnly}
                    onDelete={() => setDialog({ kind: "delete", powerId: p.powerId })}
                    onEdit={() => openEdit(p)}
                    onLevelUp={() => setDialog({ kind: "level", powerId: p.powerId, direction: "up" })}
                    onLevelDown={() => setDialog({ kind: "level", powerId: p.powerId, direction: "down" })}
                    actions={
                      play && (
                        <button type="button" onClick={() => cast(p)} title="Cast: spends the cost from your capacities"
                          className="rounded-lg bg-violet-600 px-2.5 py-1 text-xs font-semibold text-white shadow-sm hover:bg-violet-700">
                          Cast
                        </button>
                      )
                    }
                    levelUpDisabled={cost > b.expRemaining ? `Needs ${cost} EXP` : null}
                    levelDownDisabled={p.lvl <= 1 ? "Already LVL 1" : null}
                  >
                    <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-600">
                      {f.range && <span>Range {f.range}</span>}
                      {f.area && <span>Area {f.area}</span>}
                      {f.duration && <span>Duration {f.duration}</span>}
                      {f.concentration && <span>Concentration</span>}
                      {components(f) && <span>{components(f)}</span>}
                    </div>
                    <DetailText label="Material components" text={f.materialComponents} />
                    <DetailText label="Effect" text={f.effect} />
                    <DetailText label="Empower" text={f.empowerEffect} />
                    <DetailText label="Level-up effect" text={f.lvlUpEffect} />
                    <p className="text-xs text-slate-500">
                      {p.expSpent} EXP spent · next level {cost} EXP
                    </p>
                    <DetailText label="Notes" text={p.notes} />
                  </AbilityRow>
                );
              })}
            </ul>
          </Panel>
        ))}

      {!readOnly && <Fab label="Learn a power" onClick={() => setDialog({ kind: "learn" })} className="bottom-24" />}

      {dialog?.kind === "level" && current && (() => {
        const up = dialog.direction === "up";
        const cost = powerLevelCost(up ? current.lvl : current.lvl - 1);
        return (
          <LevelModal
            name={effectivePower(current).name}
            direction={dialog.direction}
            fromLevel={current.lvl}
            cost={cost}
            expRemaining={b.expRemaining}
            effect={effectivePower(current).effect ?? ""}
            lvlUpEffect={effectivePower(current).lvlUpEffect}
            onClose={() => setDialog(null)}
            onConfirm={(effect) => {
              change(current.powerId, (p) => {
                p.lvl += up ? 1 : -1;
                p.expSpent = up ? p.expSpent + cost : Math.max(0, p.expSpent - cost);
                if (effect !== (effectivePower(p).effect ?? "")) {
                  if (p.baseKnown && effect === (p.base.effect ?? "")) delete p.edits.effect;
                  else p.edits.effect = effect;
                }
              });
              setDialog(null);
            }}
          />
        );
      })()}

      {dialog?.kind === "edit" && current && (
        <EditAbilityModal<PowerFields>
          name={effectivePower(current).name}
          notes={current.notes}
          fields={POWER_EDIT_FIELDS}
          values={effectivePower(current)}
          compendium={current.baseKnown ? current.base : null}
          edited={new Set(Object.keys(current.edits))}
          onClose={() => setDialog(null)}
          onSave={(notes, values, reset) =>
            change(current.powerId, (p) => {
              p.notes = notes;
              p.edits = applyEdits(p.base, p.baseKnown, p.edits, effectivePower(p), values, reset);
            })
          }
        />
      )}

      {dialog?.kind === "delete" && current && (
        <ConfirmDialog
          title={`Forget ${effectivePower(current).name}?`}
          message={`The ${current.expSpent} EXP spent on it is refunded.`}
          confirmLabel="Forget"
          onClose={() => setDialog(null)}
          onConfirm={() => {
            update((x) => void (x.powers = x.powers.filter((p) => p.powerId !== current.powerId)));
            setDialog(null);
          }}
        />
      )}

      <Suspense fallback={null}>
        {dialog?.kind === "learn" && (
          <PowerPicker
            expRemaining={b.expRemaining}
            ownedIds={new Set(d.powers.map((p) => p.powerId))}
            onPick={(power) => update((x) => void x.powers.push(draftPower(power, POWER_COST)))}
            onClose={() => setDialog(null)}
          />
        )}
      </Suspense>
    </div>
  );
}
