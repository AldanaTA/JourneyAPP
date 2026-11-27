import { useState } from "react";
import type { Power, PowerType } from "@/models/Power";
import { formatPowerCategory } from "../../compendium/components/powers/powerFormatters";
import { POWER_CATEGORIES, POWER_TYPES } from "../prep/contentKinds";
import type { FormProps } from "./FormKit";
import { Area, Check, Chips, ContentFormModal, Num, Row, Select, Text } from "./FormKit";
import { orNull } from "./orNull";

/** Create or edit one of the campaign's powers. */
export default function PowerForm({ campaignId, initial, onDone, onClose }: FormProps<Power>) {
  const [name, setName] = useState(initial?.name ?? "");
  const [lvl, setLvl] = useState<number | null>(initial?.lvl ?? 1);
  const [type, setType] = useState<PowerType | "">(initial?.type ?? "Utility");
  const [mp, setMp] = useState<number | null>(initial?.mpCost ?? null);
  const [tp, setTp] = useState<number | null>(initial?.tpCost ?? null);
  const [hp, setHp] = useState<number | null>(initial?.hpCost ?? null);
  const [ep, setEp] = useState<number | null>(initial?.epCost ?? null);
  const [materials, setMaterials] = useState(initial?.materialComponents ?? "");
  const [verbal, setVerbal] = useState(initial?.verbal ?? false);
  const [sight, setSight] = useState(initial?.sight ?? false);
  const [somatic, setSomatic] = useState(initial?.somatic ?? false);
  const [distinct, setDistinct] = useState(initial?.isDistinct ?? false);
  const [concentration, setConcentration] = useState(initial?.concentration ?? false);
  const [range, setRange] = useState(initial?.range ?? "");
  const [area, setArea] = useState(initial?.area ?? "");
  const [duration, setDuration] = useState(initial?.duration ?? "");
  const [effect, setEffect] = useState(initial?.effect ?? "");
  const [empower, setEmpower] = useState(initial?.empowerEffect ?? "");
  const [lvlUp, setLvlUp] = useState(initial?.lvlUpEffect ?? "");
  const [categories, setCategories] = useState<string[]>(initial?.categories ?? []);

  return (
    <ContentFormModal
      kind="powers"
      singular="power"
      campaignId={campaignId}
      editingId={initial?.id ?? null}
      name={name}
      onDone={onDone}
      onClose={onClose}
      build={() => {
        if (!name.trim() || !type || !effect.trim() || !empower.trim() || !lvlUp.trim()) {
          return "A power needs a name, a type, an effect, an empower effect, and a level-up effect (write None if there is none)";
        }
        return {
          name: name.trim(), lvl: lvl ?? 1, type, mp_cost: mp, tp_cost: tp, hp_cost: hp, ep_cost: ep,
          material_components: orNull(materials), verbal, sight, somatic, is_distinct: distinct, concentration,
          range: orNull(range), area: orNull(area), duration: orNull(duration), effect: effect.trim(),
          empower_effect: empower.trim(), lvl_up_effect: lvlUp.trim(), categories,
        };
      }}
    >
      <Text label="Name" value={name} onChange={setName} />
      <Row>
        <Num label="LVL" value={lvl} onChange={setLvl} min={1} />
        <Select label="Type" value={type} options={POWER_TYPES.map((t) => ({ value: t, label: t }))} onChange={setType} />
      </Row>
      <Row>
        <Num label="MP" value={mp} onChange={setMp} />
        <Num label="TP" value={tp} onChange={setTp} />
        <Num label="HP" value={hp} onChange={setHp} />
        <Num label="EP" value={ep} onChange={setEp} />
      </Row>
      <div className="flex flex-wrap gap-x-4 gap-y-2">
        <Check label="Verbal" checked={verbal} onChange={setVerbal} />
        <Check label="Somatic" checked={somatic} onChange={setSomatic} />
        <Check label="Sight" checked={sight} onChange={setSight} />
        <Check label="Distinct" checked={distinct} onChange={setDistinct} />
        <Check label="Concentration" checked={concentration} onChange={setConcentration} />
      </div>
      <Row>
        <Text label="Range" value={range} onChange={setRange} placeholder="30ft" />
        <Text label="Area" value={area} onChange={setArea} />
        <Text label="Duration" value={duration} onChange={setDuration} />
        <Text label="Materials" value={materials} onChange={setMaterials} />
      </Row>
      <Area label="Effect" value={effect} onChange={setEffect} rows={4} />
      <Area label="Empower" value={empower} onChange={setEmpower} rows={2} />
      <Area label="Level-up effect" value={lvlUp} onChange={setLvlUp} rows={2} />
      <Chips label="Categories" values={categories} options={POWER_CATEGORIES.map((c) => ({ value: c, label: formatPowerCategory(c) }))} onChange={setCategories} />
    </ContentFormModal>
  );
}
