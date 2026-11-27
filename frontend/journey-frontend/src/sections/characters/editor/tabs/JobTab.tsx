import { lazy, Suspense, useState } from "react";
import { Check, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";
import type { AttributeName, SkillInfo } from "@/models/Character";
import { ATTRIBUTES } from "@/models/Character";
import { AdjustModal } from "../AdjustModal";
import { draftTrait, emptySkill } from "../draft";
import { useBudget, useDraft, useReadOnly, useSheet, useUpdate } from "../hooks";
import { useReferenceStore } from "../referenceStore";
import { ATTRIBUTE_NAMES, attributeValue, skillRating } from "../rules";
import { TraitInfoButton } from "../TraitInfoButton";
import { Chip, EmptyNote, Panel, Stepper, ValueRow } from "../ui";

const JobPicker = lazy(() => import("../pickers/JobPicker"));

type Open = { kind: "attribute"; name: AttributeName } | { kind: "skill"; skill: SkillInfo } | null;

/**
 * The job and its 50 job points: the job's traits are bought with them, and whatever is left can go to
 * attributes or skills (never to purchased traits or powers).
 */
export default function JobTab() {
  const d = useDraft();
  const readOnly = useReadOnly();
  const update = useUpdate();
  const sheet = useSheet();
  const b = useBudget();
  const skills = useReferenceStore((state) => state.skills);
  const [picking, setPicking] = useState(false);
  const [open, setOpen] = useState<Open>(null);

  const jobTraits = d.job?.traits ?? [];
  const left = b.jobPointsRemaining;

  return (
    <div className="space-y-4">
      <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div className="min-w-0">
            <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-700">Job</p>
            {d.job ? (
              <button
                type="button"
                disabled={readOnly}
                onClick={() => setPicking(true)}
                className="flex items-center gap-1 text-xl font-semibold text-slate-900 disabled:pointer-events-none"
              >
                <span className="truncate">{d.job.name}</span>
                {!readOnly && <ChevronRight size={18} className="text-slate-400" />}
              </button>
            ) : (
              <button
                type="button"
                disabled={readOnly}
                onClick={() => setPicking(true)}
                className="mt-1 rounded-lg border border-violet-300 bg-violet-50 px-3 py-1.5 text-sm font-semibold text-violet-700 hover:bg-violet-100"
              >
                Select job
              </button>
            )}
          </div>
          <div className="text-right">
            <div className={cn("text-2xl font-semibold tabular-nums", left < 0 ? "text-red-600" : "text-slate-900")}>{left}</div>
            <div className="text-xs text-slate-500">of {b.jobPoints} job points left</div>
          </div>
        </div>
      </section>

      <Panel title="Job traits">
        {!d.job ? (
          <EmptyNote>Choose a job to see its traits.</EmptyNote>
        ) : d.job.traits === null ? (
          <p className="text-sm text-slate-500">Loading the job's traits…</p>
        ) : jobTraits.length === 0 ? (
          <EmptyNote>This job has no traits to pick.</EmptyNote>
        ) : (
          <ul className="space-y-1.5">
            {jobTraits.map((trait) => {
              const owned = d.traits.find((t) => t.traitId === trait.id);
              const on = owned?.source === "JOB";
              const cost = trait.purchaseCost ?? 0;
              const ownedOtherwise = owned !== undefined && !on;
              const tooMuch = !on && cost > left;
              return (
                <li key={trait.id}>
                  <div className="flex items-center gap-1">
<button
                    type="button"
                    disabled={readOnly || ownedOtherwise || tooMuch}
                    onClick={() =>
                      update((x) => {
                        if (on) x.traits = x.traits.filter((t) => !(t.traitId === trait.id && t.source === "JOB"));
                        else x.traits.push(draftTrait(trait, "JOB", null, 0));
                      })
                    }
                    className={cn(
                      "flex min-w-0 flex-1 w-full items-start gap-3 rounded-xl border px-3 py-2 text-left",
                      on ? "border-violet-300 bg-violet-50" : "border-slate-200 bg-white",
                      (ownedOtherwise || tooMuch) && "opacity-55",
                    )}
                  >
                    <span
                      className={cn(
                        "mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-md border",
                        on ? "border-violet-600 bg-violet-600 text-white" : "border-slate-300 bg-white",
                      )}
                    >
                      {on && <Check size={13} strokeWidth={3} />}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-baseline justify-between gap-2">
                        <span className="text-sm font-medium text-slate-900">{trait.traitName}</span>
                        <span className="shrink-0 text-xs font-semibold tabular-nums text-violet-700">{cost} JP</span>
                      </span>
                      {trait.traitDesc && <span className="line-clamp-2 block text-xs text-slate-500">{trait.traitDesc}</span>}
                      {ownedOtherwise && <span className="block text-xs text-amber-700">Already has it</span>}
                    </span>
                  </button>
<TraitInfoButton trait={trait} />
</div>
                </li>
              );
            })}
          </ul>
        )}
      </Panel>

      <Panel title="Job points on attributes">
        {ATTRIBUTES.map((name) => (
          <ValueRow
            key={name}
            name={ATTRIBUTE_NAMES[name]}
            value={attributeValue(d, sheet, name)}
            readOnly={readOnly}
            onAdjust={() => setOpen({ kind: "attribute", name })}
            chips={<Chip label="JP" value={d.attributes[name].jobPointsSpent} tone="violet" />}
          />
        ))}
      </Panel>

      {(["COMBAT", "LIFE"] as const).map((category) => (
        <Panel key={category} title={`Job points on ${category === "COMBAT" ? "combat" : "life"} skills`} collapsible>
          {skills
            .filter((skill) => skill.category === category)
            .map((skill) => (
              <ValueRow
                key={skill.id}
                name={skill.name}
                value={`${skillRating(d, skill)}%`}
                readOnly={readOnly}
                onAdjust={() => setOpen({ kind: "skill", skill })}
                chips={<Chip label="JP" value={d.skills[skill.id]?.jobPointsSpent ?? 0} tone="violet" />}
              />
            ))}
        </Panel>
      ))}

      {open?.kind === "attribute" && (
        <AdjustModal
          title={ATTRIBUTE_NAMES[open.name]}
          valueLabel="Value"
          value={attributeValue(d, sheet, open.name)}
          pools={[{ label: "Job points", value: left, kind: "budget" }]}
          note="1 job point raises it by 1."
          onClose={() => setOpen(null)}
        >
          <Stepper
            label="Job points"
            value={d.attributes[open.name].jobPointsSpent}
            min={0}
            max={d.attributes[open.name].jobPointsSpent + Math.max(0, left)}
            onChange={(v) => update((x) => void (x.attributes[open.name].jobPointsSpent = v))}
          />
        </AdjustModal>
      )}
      {open?.kind === "skill" && (
        <AdjustModal
          title={open.skill.name}
          valueLabel="Rating"
          value={`${skillRating(d, open.skill)}%`}
          pools={[{ label: "Job points", value: left, kind: "budget" }]}
          note="1 job point raises it by 1."
          onClose={() => setOpen(null)}
        >
          <Stepper
            label="Job points"
            value={d.skills[open.skill.id]?.jobPointsSpent ?? 0}
            min={0}
            max={(d.skills[open.skill.id]?.jobPointsSpent ?? 0) + Math.max(0, left)}
            onChange={(v) =>
              update((x) => {
                x.skills[open.skill.id] ??= emptySkill();
                x.skills[open.skill.id].jobPointsSpent = v;
              })
            }
          />
        </AdjustModal>
      )}

      <Suspense fallback={null}>
        {picking && (
          <JobPicker
            currentId={d.job?.id ?? null}
            onPick={(job) =>
              update((x) => {
                x.traits = x.traits.filter((t) => t.source !== "JOB");
                x.job = { id: job.id, name: job.jobName, traits: job.traits };
              })
            }
            onClose={() => setPicking(false)}
          />
        )}
      </Suspense>
    </div>
  );
}
