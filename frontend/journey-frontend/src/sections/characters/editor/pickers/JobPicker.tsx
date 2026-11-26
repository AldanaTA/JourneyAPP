import { useState } from "react";
import { filterJobs } from "@/apis/jobApi";
import type { Job, JobCategory } from "@/models/Job";
import { useSearchSourceIds } from "../referenceStore";
import { FilterSelect, PickerModal } from "./PickerModal";

const CATEGORIES: JobCategory[] = [
  "Caster", "Companion", "Crafter", "Defender", "Divine", "Generalist", "Half-Caster", "Martial",
  "Nature", "Occult", "Ranged", "Saboteur", "Scout", "Social", "Specialist", "Support",
];

/** Pick the character's job. */
export default function JobPicker({
  currentId,
  onPick,
  onClose,
}: {
  currentId: string | null;
  onPick: (job: Job) => void;
  onClose: () => void;
}) {
  const sourceIds = useSearchSourceIds();
  const [category, setCategory] = useState<JobCategory | "">("");
  return (
    <PickerModal<Job>
      title="Choose job"
      subtitle="Changing job removes the old job's traits."
      searchPlaceholder="Search jobs"
      filters={
        <FilterSelect label="Any category" options={CATEGORIES.map((c) => ({ value: c, label: c }))} value={category} onChange={setCategory} />
      }
      filterKey={`${category}|${sourceIds.join(",")}`}
      load={(page, size, search) =>
        filterJobs(
          { categories: category ? [category] : [], matchAll: false, primaryOnly: false, page, size, name: search || null, traitName: null, sourceIds },
          page,
          size,
        )
      }
      getId={(j) => j.id}
      initialSelected={[]}
      disabledReason={(j) => (j.id === currentId ? "Current job" : null)}
      renderRow={(j) => (
        <>
          <span className="block font-semibold text-slate-900">{j.jobName}</span>
          <span className="block text-xs text-slate-500">
            {j.categories.join(" · ") || "No category"} · {j.traits.length} traits
          </span>
        </>
      )}
      renderDetails={(j) => (
        <span className="block space-y-1 text-sm text-slate-600">
          {j.jobDesc && <span className="block line-clamp-4">{j.jobDesc}</span>}
          <span className="block text-xs">
            <span className="font-semibold">Job traits:</span>{" "}
            {j.traits.map((t) => `${t.traitName} (${t.purchaseCost ?? 0})`).join(", ") || "None"}
          </span>
        </span>
      )}
      confirmLabel={() => "Choose job"}
      onConfirm={([job]) => {
        if (job) onPick(job);
        onClose();
      }}
      onClose={onClose}
    />
  );
}
