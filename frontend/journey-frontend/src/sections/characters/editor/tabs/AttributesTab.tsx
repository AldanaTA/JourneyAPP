import { useState } from "react";
import type { AttributeName, CharacterCapacitiesDto } from "@/models/Character";
import { ATTRIBUTES } from "@/models/Character";
import { AdjustModal } from "../AdjustModal";
import { useBudget, useDraft, useReadOnly, useSheet, useStats, useUpdate } from "../hooks";
import type { Stats } from "../rules";
import { ATTRIBUTE_NAMES, attributeValue, sheetBase, signed } from "../rules";
import { Chip, Panel, Stepper, ValueRow } from "../ui";

type BonusKey = keyof Pick<
  CharacterCapacitiesDto,
  "hpBonus" | "wpBonus" | "epBonus" | "mpBonus" | "physicalDamageBonus" | "magicDamageBonus" | "priorityBonus" | "movementBonus"
>;
type CurrentKey = keyof Pick<CharacterCapacitiesDto, "currentHp" | "currentWp" | "currentEp" | "currentMp">;

const CAPACITIES: { name: string; max: keyof Stats; current: CurrentKey; bonus: BonusKey; hint: string }[] = [
  { name: "HP", max: "maxHp", current: "currentHp", bonus: "hpBonus", hint: "Body" },
  { name: "WP", max: "maxWp", current: "currentWp", bonus: "wpBonus", hint: "Half of Body" },
  { name: "EP", max: "maxEp", current: "currentEp", bonus: "epBonus", hint: "Strength" },
  { name: "MP", max: "maxMp", current: "currentMp", bonus: "mpBonus", hint: "Magic" },
];

const DERIVED: { name: string; stat: keyof Stats; bonus: BonusKey; hint: string; unit?: string }[] = [
  { name: "Physical Damage", stat: "physicalDamage", bonus: "physicalDamageBonus", hint: "+2 per 15 Strength" },
  { name: "Magic Damage", stat: "magicDamage", bonus: "magicDamageBonus", hint: "+2 per 15 Magic" },
  { name: "Priority", stat: "priority", bonus: "priorityBonus", hint: "+1 per 15 Speed" },
  { name: "Movement", stat: "movementFt", bonus: "movementBonus", hint: "Race movement, +5 ft per 15 Speed", unit: " ft" },
];

type Open = { kind: "attribute"; name: AttributeName } | { kind: "capacity"; index: number } | { kind: "derived"; index: number } | null;

/** Attributes (EXP and bonuses), capacities (current, max, bonus), and the action bar's derived values. */
export default function AttributesTab() {
  const d = useDraft();
  const readOnly = useReadOnly();
  const update = useUpdate();
  const sheet = useSheet();
  const s = useStats();
  const b = useBudget();
  const [open, setOpen] = useState<Open>(null);

  return (
    <div className="space-y-4">
      {!sheet && (
        <p className="rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-800">Choose a character sheet on the Identity tab to get starting attributes.</p>
      )}

      <Panel title="Attributes">
        {ATTRIBUTES.map((name) => {
          const a = d.attributes[name];
          return (
            <ValueRow
              key={name}
              name={ATTRIBUTE_NAMES[name]}
              value={attributeValue(d, sheet, name)}
              readOnly={readOnly}
              onAdjust={() => setOpen({ kind: "attribute", name })}
              chips={
                <>
                  <Chip label="EXP" value={a.expSpent} tone="violet" />
                  {a.jobPointsSpent > 0 && <Chip label="JP" value={a.jobPointsSpent} />}
                  <Chip label="Bonus" value={signed(a.bonus)} tone="amber" />
                </>
              }
            />
          );
        })}
      </Panel>

      <Panel title="Capacity">
        {CAPACITIES.map((c, index) => {
          const max = s[c.max];
          const current = d.capacities[c.current] ?? max;
          return (
            <ValueRow
              key={c.name}
              name={c.name}
              value={
                <span>
                  {current}
                  <span className="text-sm font-normal text-slate-400"> / {max}</span>
                </span>
              }
              readOnly={readOnly}
              onAdjust={() => setOpen({ kind: "capacity", index })}
              chips={<Chip label="Bonus" value={signed(d.capacities[c.bonus])} tone="amber" />}
            />
          );
        })}
      </Panel>

      <Panel title="Action bar">
        {DERIVED.map((x, index) => (
          <ValueRow
            key={x.name}
            name={x.name}
            value={`${s[x.stat]}${x.unit ?? ""}`}
            readOnly={readOnly}
            onAdjust={() => setOpen({ kind: "derived", index })}
            chips={<Chip label="Bonus" value={signed(d.capacities[x.bonus])} tone="amber" />}
          />
        ))}
      </Panel>

      {open?.kind === "attribute" && (
        <AdjustModal
          title={ATTRIBUTE_NAMES[open.name]}
          valueLabel="Value"
          value={attributeValue(d, sheet, open.name)}
          pools={[{ label: "EXP", value: b.expRemaining, kind: "budget" }]}
          note={`Sheet base ${sheetBase(sheet, open.name)}. 1 EXP raises it by 1.`}
          onClose={() => setOpen(null)}
        >
          <Stepper
            label="EXP spent"
            value={d.attributes[open.name].expSpent}
            min={0}
            max={d.attributes[open.name].expSpent + Math.max(0, b.expRemaining)}
            onChange={(v) => update((x) => void (x.attributes[open.name].expSpent = v))}
          />
          <Stepper
            label="Bonus"
            hint="From traits, items, or the storyteller"
            value={d.attributes[open.name].bonus}
            onChange={(v) => update((x) => void (x.attributes[open.name].bonus = v))}
          />
        </AdjustModal>
      )}

      {open?.kind === "capacity" && (() => {
        const c = CAPACITIES[open.index];
        const max = s[c.max];
        return (
          <AdjustModal
            title={c.name}
            valueLabel="Current / max"
            value={`${d.capacities[c.current] ?? max} / ${max}`}
            note={`Max = ${c.hint} + bonus.`}
            onClose={() => setOpen(null)}
          >
            <Stepper label="Bonus" hint="Extra maximum" value={d.capacities[c.bonus]} onChange={(v) => update((x) => void (x.capacities[c.bonus] = v))} />
            <Stepper
              label="Current"
              value={d.capacities[c.current] ?? max}
              min={0}
              onChange={(v) => update((x) => void (x.capacities[c.current] = v))}
            />
          </AdjustModal>
        );
      })()}

      {open?.kind === "derived" && (() => {
        const x = DERIVED[open.index];
        return (
          <AdjustModal title={x.name} valueLabel="Value" value={`${s[x.stat]}${x.unit ?? ""}`} note={x.hint} onClose={() => setOpen(null)}>
            <Stepper
              label="Bonus"
              value={d.capacities[x.bonus]}
              step={x.bonus === "movementBonus" ? 5 : 1}
              onChange={(v) => update((y) => void (y.capacities[x.bonus] = v))}
            />
          </AdjustModal>
        );
      })()}
    </div>
  );
}
