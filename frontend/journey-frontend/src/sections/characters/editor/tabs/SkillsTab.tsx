import { useState } from "react";
import { cn } from "@/lib/utils";
import type { SkillCategory, SkillInfo } from "@/models/Character";
import { AdjustModal } from "../AdjustModal";
import type { DraftSkill } from "../draft";
import { emptySkill } from "../draft";
import { useBudget, useDraft, useReadOnly, useUpdate } from "../hooks";
import { usePlay } from "../playContext";
import { useReferenceStore } from "../referenceStore";
import { MAX_SKILL_SWAP, signed, skillRating } from "../rules";
import { Chip, Panel, Stepper, ValueRow } from "../ui";

const GROUPS: { category: SkillCategory; title: string }[] = [
  { category: "COMBAT", title: "Combat skills" },
  { category: "LIFE", title: "Life skills" },
];

/**
 * Skill ratings with failures, EXP, and bonuses. A skill can also be swapped (during creation, and later if the table allows)
 * down by up to 20 to raise another skill of the same kind by as much (each kind must balance to 0).
 */
export default function SkillsTab() {
  const d = useDraft();
  const readOnly = useReadOnly();
  const update = useUpdate();
  const b = useBudget();
  const skills = useReferenceStore((state) => state.skills);
  const [open, setOpen] = useState<SkillInfo | null>(null);
  const play = usePlay();

  const set = (skill: SkillInfo, field: keyof DraftSkill, value: number) =>
    update((x) => {
      x.skills[skill.id] ??= emptySkill();
      x.skills[skill.id][field] = value;
    });

  const row = (skill: SkillInfo) => d.skills[skill.id] ?? emptySkill();

  return (
    <div className="space-y-4">
      {GROUPS.map(({ category, title }) => {
        const swap = category === "COMBAT" ? b.combatSwap : b.lifeSwap;
        return (
          <Panel
            key={category}
            title={title}
            collapsible
            action={
              <span className={cn("text-xs font-semibold tabular-nums", swap === 0 ? "text-slate-400" : "text-amber-700")}>
                Swaps {signed(swap)}
              </span>
            }
          >
            {skills
              .filter((s) => s.category === category)
              .map((skill) => {
                const s = row(skill);
                return (
                  <ValueRow
                    key={skill.id}
                    name={skill.name}
                    value={`${skillRating(d, skill)}%`}
                    readOnly={readOnly}
                    onAdjust={() => setOpen(skill)}
                    onTap={play ? () => play.rollSkill(skill.name, skillRating(d, skill)) : undefined}
                    tapLabel={`Roll ${skill.name}`}
                    chips={
                      <>
                        {s.failures > 0 && <Chip label="Fail" value={s.failures} tone="slate" />}
                        <Chip label="EXP" value={s.expSpent} tone="violet" />
                        <Chip label="Bonus" value={signed(s.permanentModifier + s.temporaryModifier)} tone="amber" />
                        {s.creationAdjustment !== 0 && <Chip label="Swap" value={signed(s.creationAdjustment)} />}
                      </>
                    }
                  />
                );
              })}
          </Panel>
        );
      })}

      {open && (() => {
        const s = row(open);
        return (
          <AdjustModal
            title={open.name}
            valueLabel="Rating"
            value={`${skillRating(d, open)}%`}
            pools={[
              { label: "EXP", value: b.expRemaining, kind: "budget" },
              { label: `${open.category === "COMBAT" ? "Combat" : "Life"} swaps`, value: open.category === "COMBAT" ? b.combatSwap : b.lifeSwap, kind: "balance" as const },
            ]}
            note={`Base ${open.baseRating}%${s.jobPointsSpent ? `, ${s.jobPointsSpent} from job points` : ""}. 1 EXP raises it by 1. Three failures can be erased on a rest to raise the rating by 1d6 (add it as a bonus).`}
            onClose={() => setOpen(null)}
          >
            <Stepper
              label="EXP spent"
              value={s.expSpent}
              min={0}
              max={s.expSpent + Math.max(0, b.expRemaining)}
              onChange={(v) => set(open, "expSpent", v)}
            />
            <Stepper label="Bonus" hint="Lasting: 1d6 raises, skill books, traits" value={s.permanentModifier} onChange={(v) => set(open, "permanentModifier", v)} />
            <Stepper label="Temporary" hint="Equipment, powers, conditions" value={s.temporaryModifier} onChange={(v) => set(open, "temporaryModifier", v)} />
            <Stepper label="Failures" hint="1 per failure, 2 per fumble" value={s.failures} min={0} onChange={(v) => set(open, "failures", v)} />
            {/* Open after creation too: some tables let players rebalance their swaps later. */}
            <Stepper
              label="Creation swap"
              hint={`Lower by up to ${MAX_SKILL_SWAP} to raise another ${open.category === "COMBAT" ? "combat" : "life"} skill`}
              value={s.creationAdjustment}
              min={-MAX_SKILL_SWAP}
              onChange={(v) => set(open, "creationAdjustment", v)}
            />
          </AdjustModal>
        );
      })()}
    </div>
  );
}
