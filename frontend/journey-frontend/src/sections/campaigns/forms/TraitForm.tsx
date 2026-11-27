import { useState } from "react";
import type { Trait } from "@/models/Trait";
import { formatTraitCategory } from "../../compendium/components/traits/traitFormatters";
import { TRAIT_CATEGORIES } from "../prep/contentKinds";
import type { FormProps } from "./FormKit";
import { Area, Chips, ContentFormModal, Num, Row, Text } from "./FormKit";
import { orNull } from "./orNull";

/** Create or edit one of the campaign's traits. */
export default function TraitForm({ campaignId, initial, onDone, onClose }: FormProps<Trait>) {
  const [name, setName] = useState(initial?.traitName ?? "");
  const [lvl, setLvl] = useState<number | null>(initial?.lvl ?? 1);
  const [cost, setCost] = useState<number | null>(initial?.purchaseCost ?? 10);
  const [lvlUpCost, setLvlUpCost] = useState<number | null>(initial?.lvlUpCost ?? 5);
  const [uses, setUses] = useState<number | null>(initial?.traitUses ?? 0);
  const [mp, setMp] = useState<number | null>(initial?.mpCost ?? 0);
  const [ep, setEp] = useState<number | null>(initial?.epCost ?? 0);
  const [tp, setTp] = useState<number | null>(initial?.tpCost ?? 0);
  const [desc, setDesc] = useState(initial?.traitDesc ?? "");
  const [effect, setEffect] = useState(initial?.traitEffect ?? "");
  const [lvlUp, setLvlUp] = useState(initial?.lvlUpEffect ?? "");
  const [categories, setCategories] = useState<string[]>(initial?.categories ?? []);

  return (
    <ContentFormModal
      kind="traits"
      singular="trait"
      campaignId={campaignId}
      editingId={initial?.id ?? null}
      name={name}
      onDone={onDone}
      onClose={onClose}
      build={() => {
        if (!name.trim() || !desc.trim() || !effect.trim()) return "A trait needs a name, a description, and an effect";
        return {
          trait_name: name.trim(), lvl: lvl ?? 1, purchase_cost: cost ?? 0, lvl_up_cost: lvlUpCost ?? 0, trait_desc: desc.trim(),
          trait_uses: uses ?? 0, mp_cost: mp ?? 0, ep_cost: ep ?? 0, tp_cost: tp ?? 0, trait_effect: effect.trim(),
          lvl_up_effect: orNull(lvlUp), categories,
        };
      }}
    >
      <Text label="Name" value={name} onChange={setName} />
      <Row>
        <Num label="LVL" value={lvl} onChange={setLvl} min={1} />
        <Num label="Cost (EXP)" value={cost} onChange={setCost} />
        <Num label="LVL up cost" value={lvlUpCost} onChange={setLvlUpCost} />
        <Num label="Uses" value={uses} onChange={setUses} />
      </Row>
      <Row>
        <Num label="MP" value={mp} onChange={setMp} />
        <Num label="EP" value={ep} onChange={setEp} />
        <Num label="TP" value={tp} onChange={setTp} />
      </Row>
      <Area label="Description" value={desc} onChange={setDesc} rows={2} />
      <Area label="Effect" value={effect} onChange={setEffect} rows={4} />
      <Area label="Level-up effect" value={lvlUp} onChange={setLvlUp} rows={2} />
      <Chips label="Categories" values={categories} options={TRAIT_CATEGORIES.map((c) => ({ value: c, label: formatTraitCategory(c) }))} onChange={setCategories} />
    </ContentFormModal>
  );
}
