import { useState } from "react";
import type { Race, RaceSize } from "@/models/Race";
import { SIZES, SIZE_NAMES } from "../prep/contentKinds";
import type { FormProps } from "./FormKit";
import { Area, ContentFormModal, Num, Row, Select, Text } from "./FormKit";
import { orNull } from "./orNull";
import { TraitChooser } from "./TraitChooser";

/** Create or edit one of the campaign's races and its racial traits. */
export default function RaceForm({ campaignId, initial, onDone, onClose }: FormProps<Race>) {
  const [name, setName] = useState(initial?.raceName ?? "");
  const [minSize, setMinSize] = useState<RaceSize | "">(initial?.minRaceSize ?? "M");
  const [maxSize, setMaxSize] = useState<RaceSize | "">(initial?.maxRaceSize ?? "");
  const [movement, setMovement] = useState<number | null>(initial?.movement ?? 30);
  const [languages, setLanguages] = useState(initial?.languages ?? "");
  const [description, setDescription] = useState(initial?.raceDescription ?? "");
  const [traits, setTraits] = useState(initial?.traits.map((t) => ({ id: t.id, name: t.traitName })) ?? []);

  return (
    <ContentFormModal
      kind="races"
      singular="race"
      campaignId={campaignId}
      editingId={initial?.id ?? null}
      name={name}
      onDone={onDone}
      onClose={onClose}
      build={() => {
        if (!name.trim() || !minSize) return "A race needs a name and a size";
        return {
          race_name: name.trim(), min_race_size: minSize, max_race_size: maxSize || null, movement: movement ?? 30,
          languages: orNull(languages), race_description: orNull(description), trait_ids: traits.map((t) => t.id),
        };
      }}
    >
      <Text label="Name" value={name} onChange={setName} />
      <Row>
        <Select label="Size" value={minSize} options={SIZES.map((s) => ({ value: s, label: SIZE_NAMES[s] }))} onChange={setMinSize} />
        <Select label="Up to size" value={maxSize} allowEmpty="—" options={SIZES.map((s) => ({ value: s, label: SIZE_NAMES[s] }))} onChange={setMaxSize} />
        <Num label="Movement (ft)" value={movement} onChange={setMovement} />
      </Row>
      <Text label="Language set" value={languages} onChange={setLanguages} placeholder="Common and Aquan" />
      <Area label="Description" value={description} onChange={setDescription} rows={3} />
      <TraitChooser campaignId={campaignId} label="Racial traits" chosen={traits} onChange={setTraits} />
    </ContentFormModal>
  );
}
