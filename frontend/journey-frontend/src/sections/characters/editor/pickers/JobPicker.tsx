import { lazy, Suspense, useState } from "react";
import { filterJobs } from "@/apis/jobApi";
import type { Job } from "@/models/Job";
import { useSearchSourceIds } from "../referenceStore";
import { PickerModal } from "./PickerModal";
import type { JobFilterRequest } from "@/apis/jobApi";
import { JobFilter } from "@/sections/compendium/components/filters/JobFilter";

const NO_JOB_FILTERS: JobFilterRequest = { categories: [], matchAll: false, primaryOnly: false, page: 0, size: 20, name: null, traitName: null };

const JobDetailsModal = lazy(() =>
  import("@/sections/compendium/components/jobs/JobDetailsModal").then((m) => ({ default: m.JobDetailsModal })),
);

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
  const [request, setRequest] = useState<JobFilterRequest | null>(null);
  return (
    <PickerModal<Job>
      title="Choose job"
      subtitle="Changing job removes the old job's traits."
      searchPlaceholder="Search jobs"
      hideSearch
      filters={<div className="w-full"><JobFilter onApply={setRequest} initialFilters={request} /></div>}
      filterKey={`${JSON.stringify(request)}|${sourceIds.join(",")}`}
      load={(page, size) => filterJobs({ ...(request ?? NO_JOB_FILTERS), sourceIds, page, size }, page, size)}
      getId={(j) => j.id}
      renderFullDetails={(j, close) => (
        <Suspense fallback={null}>
          <JobDetailsModal job={j} isOpen onClose={close} />
        </Suspense>
      )}
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
