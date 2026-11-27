import { useState } from "react";
import type { Job } from "@/models/Job";
import { JOB_CATEGORIES } from "../prep/contentKinds";
import type { FormProps } from "./FormKit";
import { Area, Chips, ContentFormModal, Select, Text } from "./FormKit";
import { TraitChooser } from "./TraitChooser";

/** Create or edit one of the campaign's jobs and the traits it offers for job points. */
export default function JobForm({ campaignId, initial, onDone, onClose }: FormProps<Job>) {
  const [name, setName] = useState(initial?.jobName ?? "");
  const [description, setDescription] = useState(initial?.jobDesc ?? "");
  const [categories, setCategories] = useState<string[]>(initial?.categories ?? []);
  const [primary, setPrimary] = useState<string>(initial?.primaryCategory ?? "");
  const [traits, setTraits] = useState(initial?.traits.map((t) => ({ id: t.id, name: t.traitName })) ?? []);

  return (
    <ContentFormModal
      kind="jobs"
      singular="job"
      campaignId={campaignId}
      editingId={initial?.id ?? null}
      name={name}
      onDone={onDone}
      onClose={onClose}
      build={() => {
        if (!name.trim() || !description.trim()) return "A job needs a name and a description";
        if (traits.length === 0) return "A job needs at least one trait";
        return {
          job_name: name.trim(), job_desc: description.trim(), trait_ids: traits.map((t) => t.id),
          categories, primary_category: primary || null,
        };
      }}
    >
      <Text label="Name" value={name} onChange={setName} />
      <Area label="Description" value={description} onChange={setDescription} rows={3} />
      <Chips label="Categories" values={categories} options={JOB_CATEGORIES.map((c) => ({ value: c, label: c }))} onChange={(v) => {
        setCategories(v);
        if (primary && !v.includes(primary)) setPrimary("");
      }} />
      <Select label="Primary category" value={primary} allowEmpty="—" options={categories.map((c) => ({ value: c, label: c }))} onChange={setPrimary} />
      <TraitChooser campaignId={campaignId} label="Job traits (bought with job points)" chosen={traits} onChange={setTraits} />
    </ContentFormModal>
  );
}
