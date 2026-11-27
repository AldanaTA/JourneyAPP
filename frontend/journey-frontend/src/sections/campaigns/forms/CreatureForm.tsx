import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Plus, X } from "lucide-react";
import { getCreatureTypes } from "@/apis/creatureApi";
import { getItemReferenceData } from "@/apis/itemApi";
import type { AbilityTiming, AugmentKind, Creature, CreatureTypeInfo } from "@/models/Creature";
import type { ItemReferenceData } from "@/models/Item";
import type { RaceSize } from "@/models/Race";
import { SIZES, SIZE_NAMES } from "../prep/contentKinds";
import type { FormProps } from "./FormKit";
import { Area, ContentFormModal, Num, Row, Select, Text } from "./FormKit";
import { orNull } from "./orNull";

interface Ability {
  timing: AbilityTiming;
  name: string;
  effect: string;
  uses: number | null;
  mp: number | null;
  ep: number | null;
  tp: number | null;
}

interface Attack {
  name: string;
  skill: string;
  rangeFt: number | null;
  dice: string;
  bonus: string;
  damageType: string;
  mag: number | null;
}

/** A list of rows with add and remove. */
function ListBlock({ title, onAdd, children }: { title: string; onAdd: () => void; children: ReactNode }) {
  return (
    <div className="space-y-2 rounded-xl border border-slate-200 p-3">
      <div className="flex items-center justify-between">
        <h4 className="text-xs font-bold uppercase tracking-wide text-violet-700">{title}</h4>
        <button type="button" onClick={onAdd} className="flex items-center gap-1 text-xs font-semibold text-violet-700">
          <Plus size={14} /> Add
        </button>
      </div>
      {children}
    </div>
  );
}

function RemoveButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} aria-label="Remove" className="self-end rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-red-600">
      <X size={16} />
    </button>
  );
}

/** Create or edit one of the campaign's creatures (storytellers only see these). */
export default function CreatureForm({ campaignId, initial, onDone, onClose }: FormProps<Creature>) {
  const [types, setTypes] = useState<CreatureTypeInfo[]>([]);
  const [reference, setReference] = useState<ItemReferenceData | null>(null);
  const [name, setName] = useState(initial?.name ?? "");
  const [type, setType] = useState(initial?.creatureType ?? "");
  const [threat, setThreat] = useState<number | null>(initial?.threatLvl ?? 1);
  const [minSize, setMinSize] = useState<RaceSize | "">(initial?.minSize ?? "M");
  const [maxSize, setMaxSize] = useState<RaceSize | "">(initial?.maxSize ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [languages, setLanguages] = useState(initial?.languages ?? "");
  const [lore, setLore] = useState(initial?.lore ?? "");
  const [hp, setHp] = useState<number | null>(initial?.hp ?? 10);
  const [wp, setWp] = useState<number | null>(initial?.wp ?? null);
  const [mp, setMp] = useState<number | null>(initial?.mp ?? 0);
  const [ep, setEp] = useState<number | null>(initial?.ep ?? 0);
  const [prio, setPrio] = useState<number | null>(initial?.prio ?? 0);
  const [movement, setMovement] = useState<number | null>(initial?.movement ?? 30);
  const [pd, setPd] = useState<number | null>(initial?.pd ?? 0);
  const [md, setMd] = useState<number | null>(initial?.md ?? 0);
  const [skills, setSkills] = useState(initial?.skills.map((s) => ({ skill: s.skill, rating: s.rating as number | null })) ?? []);
  const [augments, setAugments] = useState<{ kind: AugmentKind; target: string }[]>(
    initial?.augments.map((a) => ({ kind: a.kind, target: a.target })) ?? [],
  );
  const [abilities, setAbilities] = useState<Ability[]>(
    initial?.abilities.map((a) => ({ timing: a.timing, name: a.name, effect: a.effect, uses: a.uses, mp: a.mpCost, ep: a.epCost, tp: a.tpCost })) ?? [],
  );
  const [attacks, setAttacks] = useState<Attack[]>(
    initial?.attacks.map((a) => ({
      name: a.name, skill: a.skill, rangeFt: a.rangeFt, dice: a.damageDice, bonus: a.damageBonus ?? "", damageType: a.damageType.name, mag: a.mag,
    })) ?? [],
  );

  useEffect(() => {
    getCreatureTypes(campaignId)
      .then((list) => {
        setTypes(list);
        setType((current) => current || list[0]?.name || "");
      })
      .catch(() => undefined);
    getItemReferenceData().then(setReference).catch(() => undefined);
  }, [campaignId]);

  const edit = <T,>(list: T[], set: (v: T[]) => void, index: number, patch: Partial<T>) =>
    set(list.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  const damageTypes = (reference?.damageTypes ?? []).map((d) => ({ value: d.name, label: d.name }));

  return (
    <ContentFormModal
      kind="creatures"
      singular="creature"
      campaignId={campaignId}
      editingId={initial?.id ?? null}
      name={name}
      onDone={onDone}
      onClose={onClose}
      build={() => {
        if (!name.trim() || !type || !minSize || hp === null) return "A creature needs a name, a type, a size, and HP";
        if (abilities.some((a) => !a.name.trim() || !a.effect.trim())) return "Every ability needs a name and an effect";
        if (attacks.some((a) => !a.name.trim() || !a.skill.trim() || !a.damageType || !/^\d+d\d+$/i.test(a.dice.trim()))) {
          return "Every attack needs a name, a skill, damage dice like 2d6, and a damage type";
        }
        return {
          name: name.trim(), creature_type: type, threat_lvl: threat ?? 1, min_size: minSize, max_size: maxSize || null,
          description: orNull(description), languages: orNull(languages), lore: orNull(lore),
          hp, wp, mp, ep, prio, movement, pd, md,
          skills: skills.filter((s) => s.skill.trim()).map((s) => ({ skill: s.skill.trim(), rating: s.rating ?? 0 })),
          augments: augments.filter((a) => a.target.trim()).map((a) => ({ kind: a.kind, target: a.target.trim() })),
          abilities: abilities.map((a) => ({
            timing: a.timing, name: a.name.trim(), effect: a.effect.trim(), uses: a.uses, mp_cost: a.mp, ep_cost: a.ep, tp_cost: a.tp,
          })),
          attacks: attacks.map((a) => ({
            name: a.name.trim(), skill: a.skill.trim(), range_ft: a.rangeFt, damage_dice: a.dice.trim(), damage_bonus: orNull(a.bonus),
            damage_type: a.damageType, mag: a.mag, properties: [],
          })),
        };
      }}
    >
      <Text label="Name" value={name} onChange={setName} />
      <Row>
        <Select label="Type" value={type} options={types.map((t) => ({ value: t.name, label: t.name }))} onChange={setType} />
        <Num label="Threat LVL" value={threat} onChange={setThreat} min={1} />
        <Select label="Size" value={minSize} options={SIZES.map((s) => ({ value: s, label: SIZE_NAMES[s] }))} onChange={setMinSize} />
        <Select label="Up to size" value={maxSize} allowEmpty="—" options={SIZES.map((s) => ({ value: s, label: SIZE_NAMES[s] }))} onChange={setMaxSize} />
      </Row>
      <Row>
        <Num label="HP" value={hp} onChange={setHp} />
        <Num label="WP" value={wp} onChange={setWp} />
        <Num label="MP" value={mp} onChange={setMp} />
        <Num label="EP" value={ep} onChange={setEp} />
      </Row>
      <Row>
        <Num label="Priority" value={prio} onChange={setPrio} min={-100} />
        <Num label="Movement (ft)" value={movement} onChange={setMovement} />
        <Num label="PD" value={pd} onChange={setPd} min={-100} />
        <Num label="MD" value={md} onChange={setMd} min={-100} />
      </Row>
      <Area label="Description" value={description} onChange={setDescription} rows={2} />
      <Text label="Languages" value={languages} onChange={setLanguages} />
      <Area label="Lore" value={lore} onChange={setLore} rows={2} />

      <ListBlock title="Skills" onAdd={() => setSkills([...skills, { skill: "", rating: 50 }])}>
        {skills.map((s, i) => (
          <div key={i} className="flex gap-2">
            <div className="flex-1">
              <Text label="Skill" value={s.skill} onChange={(v) => edit(skills, setSkills, i, { skill: v })} />
            </div>
            <div className="w-24">
              <Num label="Rating" value={s.rating} onChange={(v) => edit(skills, setSkills, i, { rating: v })} />
            </div>
            <RemoveButton onClick={() => setSkills(skills.filter((_, j) => j !== i))} />
          </div>
        ))}
      </ListBlock>

      <ListBlock title="Weak, resistant, immune" onAdd={() => setAugments([...augments, { kind: "RESISTANT", target: "" }])}>
        {augments.map((a, i) => (
          <div key={i} className="flex gap-2">
            <div className="w-32">
              <Select
                label="Kind"
                value={a.kind}
                options={[{ value: "WEAK", label: "Weak" }, { value: "RESISTANT", label: "Resistant" }, { value: "IMMUNE", label: "Immune" }]}
                onChange={(v) => v && edit(augments, setAugments, i, { kind: v as AugmentKind })}
              />
            </div>
            <div className="flex-1">
              <Text label="To (damage type or condition)" value={a.target} onChange={(v) => edit(augments, setAugments, i, { target: v })} />
            </div>
            <RemoveButton onClick={() => setAugments(augments.filter((_, j) => j !== i))} />
          </div>
        ))}
      </ListBlock>

      <ListBlock
        title="Abilities"
        onAdd={() => setAbilities([...abilities, { timing: "DECLARED", name: "", effect: "", uses: null, mp: null, ep: null, tp: null }])}
      >
        {abilities.map((a, i) => (
          <div key={i} className="space-y-2 rounded-lg bg-slate-50 p-2">
            <div className="flex gap-2">
              <div className="w-32">
                <Select
                  label="Timing"
                  value={a.timing}
                  options={[{ value: "PASSIVE", label: "Passive" }, { value: "TRIGGERED", label: "Triggered" }, { value: "DECLARED", label: "Declared" }]}
                  onChange={(v) => v && edit(abilities, setAbilities, i, { timing: v as AbilityTiming })}
                />
              </div>
              <div className="flex-1">
                <Text label="Name" value={a.name} onChange={(v) => edit(abilities, setAbilities, i, { name: v })} />
              </div>
              <RemoveButton onClick={() => setAbilities(abilities.filter((_, j) => j !== i))} />
            </div>
            <Area label="Effect" value={a.effect} onChange={(v) => edit(abilities, setAbilities, i, { effect: v })} rows={2} />
            <Row>
              <Num label="Uses" value={a.uses} onChange={(v) => edit(abilities, setAbilities, i, { uses: v })} />
              <Num label="MP" value={a.mp} onChange={(v) => edit(abilities, setAbilities, i, { mp: v })} />
              <Num label="EP" value={a.ep} onChange={(v) => edit(abilities, setAbilities, i, { ep: v })} />
              <Num label="TP" value={a.tp} onChange={(v) => edit(abilities, setAbilities, i, { tp: v })} />
            </Row>
          </div>
        ))}
      </ListBlock>

      <ListBlock
        title="Attacks"
        onAdd={() => setAttacks([...attacks, { name: "", skill: "", rangeFt: null, dice: "1d6", bonus: "", damageType: damageTypes[0]?.value ?? "", mag: null }])}
      >
        {attacks.map((a, i) => (
          <div key={i} className="space-y-2 rounded-lg bg-slate-50 p-2">
            <div className="flex gap-2">
              <div className="flex-1">
                <Text label="Name" value={a.name} onChange={(v) => edit(attacks, setAttacks, i, { name: v })} />
              </div>
              <div className="flex-1">
                <Text label="Skill" value={a.skill} onChange={(v) => edit(attacks, setAttacks, i, { skill: v })} />
              </div>
              <RemoveButton onClick={() => setAttacks(attacks.filter((_, j) => j !== i))} />
            </div>
            <Row>
              <Text label="Dice" value={a.dice} onChange={(v) => edit(attacks, setAttacks, i, { dice: v })} />
              <Text label="Bonus" value={a.bonus} onChange={(v) => edit(attacks, setAttacks, i, { bonus: v })} />
              <Select label="Damage type" value={a.damageType} allowEmpty="Choose…" options={damageTypes} onChange={(v) => edit(attacks, setAttacks, i, { damageType: v })} />
              <Num label="Range (ft)" value={a.rangeFt} onChange={(v) => edit(attacks, setAttacks, i, { rangeFt: v })} />
            </Row>
          </div>
        ))}
      </ListBlock>
    </ContentFormModal>
  );
}
