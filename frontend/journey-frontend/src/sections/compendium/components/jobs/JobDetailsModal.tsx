import { useEffect } from "react";
import { createPortal } from "react-dom";
import { ChevronDown } from "lucide-react";
import type { Job } from "../../../../models/Job";
import type { Trait } from "../../../../models/Trait";
import {
  formatTraitActivationCost,
  formatTraitCategory,
  formatTraitUses,
  meaningfulText,
} from "../traits/traitFormatters";
import { formatJobTraitCount, totalJobPurchaseCost } from "./jobFormatters";

interface JobDetailsModalProps {
  job: Job;
  isOpen: boolean;
  onClose: () => void;
}

/** One expandable trait row inside the job details; collapsed it shows the key numbers, expanded the full rules. */
function JobTraitItem({ trait }: { trait: Trait }) {
  const activationCost = formatTraitActivationCost(trait);
  const traitDesc = meaningfulText(trait.traitDesc);
  const lvlUpEffect = meaningfulText(trait.lvlUpEffect);

  return (
    <details className="group rounded-xl border border-slate-700 bg-slate-800/60 open:border-slate-500">
      <summary className="flex cursor-pointer list-none items-start justify-between gap-3 p-3 [&::-webkit-details-marker]:hidden">
        <div className="min-w-0">
          <h4 className="truncate font-semibold text-white">{trait.traitName}</h4>
          <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-[11px] text-slate-300">
            <span className="rounded-full bg-slate-900 px-2 py-0.5">LVL {trait.lvl ?? "—"}</span>
            <span className="rounded-full bg-slate-900 px-2 py-0.5">Cost {trait.purchaseCost ?? "—"}</span>
            <span className="rounded-full bg-slate-900 px-2 py-0.5">{formatTraitUses(trait)}</span>
            {activationCost.length > 0 && (
              <span className="rounded-full bg-slate-900 px-2 py-0.5">{activationCost.join(" • ")}</span>
            )}
          </div>
        </div>
        <ChevronDown
          className="mt-1 h-4 w-4 shrink-0 text-slate-400 transition-transform group-open:rotate-180"
          aria-hidden="true"
        />
      </summary>

      <div className="space-y-3 border-t border-slate-700 p-3 text-sm">
        <div className="text-slate-300">
          <span className="font-medium">LVL Up Cost:</span> {trait.lvlUpCost || "—"}
        </div>

        {traitDesc && <p className="whitespace-pre-line leading-relaxed text-slate-300">{traitDesc}</p>}

        <div>
          <h5 className="text-xs font-semibold uppercase tracking-wide text-slate-300">Effect</h5>
          <p className="mt-1 whitespace-pre-line leading-relaxed text-slate-200">
            {trait.traitEffect ?? "No effect description provided."}
          </p>
        </div>

        {lvlUpEffect && (
          <div>
            <h5 className="text-xs font-semibold uppercase tracking-wide text-cyan-300">Level Up</h5>
            <p className="mt-1 whitespace-pre-line leading-relaxed text-slate-200">{lvlUpEffect}</p>
          </div>
        )}

        {trait.categories.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {trait.categories.map((category) => (
              <span
                key={category}
                className="rounded-full border border-violet-500/40 bg-violet-500/10 px-2 py-1 text-[10px] font-medium uppercase tracking-wide text-violet-200"
              >
                {formatTraitCategory(category)}
              </span>
            ))}
          </div>
        )}
      </div>
    </details>
  );
}

/**
 * Full-detail view of a job and its traits. It slides up as a bottom sheet on phones and is a centered
 * dialog on larger screens; it closes on backdrop tap, the close button, or the Escape key.
 */
export function JobDetailsModal({ job, isOpen, onClose }: JobDetailsModalProps) {
  /** Closes the modal when Escape is pressed and locks background scrolling while it is open. */
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  // Portaled to <body> so ancestors with backdrop-filter/transform cannot trap the fixed overlay.
  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/80 backdrop-blur-sm sm:items-center sm:p-4"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="job-details-title"
        className="max-h-[90dvh] w-full max-w-2xl overflow-y-auto overscroll-contain rounded-t-2xl border border-slate-700 bg-slate-900 p-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] text-slate-100 shadow-2xl sm:rounded-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="mx-auto mb-3 h-1.5 w-10 rounded-full bg-slate-700 sm:hidden" aria-hidden="true" />

        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-xs uppercase tracking-[0.2em] text-violet-300">Job</p>
            <h2 id="job-details-title" className="mt-1 text-2xl font-bold text-white">{job.jobName}</h2>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close job details"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-slate-600 bg-slate-800 text-slate-200 hover:bg-slate-700"
            title="Close"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="h-4 w-4">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-2 text-xs text-slate-300">
          {job.categories.map((category) => (
            <span
              key={category}
              className={
                category === job.primaryCategory
                  ? "rounded-full border border-violet-500/60 bg-violet-500/20 px-2 py-1 font-medium text-violet-100"
                  : "rounded-full border border-violet-500/40 bg-violet-500/10 px-2 py-1 text-violet-200"
              }
            >
              {category}
              {category === job.primaryCategory && <span className="sr-only"> (primary)</span>}
            </span>
          ))}
          <span className="rounded-full bg-slate-800 px-2 py-1">{formatJobTraitCount(job)}</span>
        </div>

        <div className="mt-5 grid grid-cols-2 gap-3 text-sm text-slate-200">
          <div>
            <span className="font-medium text-slate-300">Total Cost:</span> {totalJobPurchaseCost(job)} EXP
          </div>
          <div>
            <span className="font-medium text-slate-300">Source:</span> {job.contentSourceName ?? "—"}
          </div>
        </div>

        {job.jobDesc && (
          <div className="mt-5">
            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-300">Description</h3>
            <p className="mt-2 whitespace-pre-line leading-relaxed text-slate-200">{job.jobDesc}</p>
          </div>
        )}

        <div className="mt-5">
          <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-300">Traits</h3>
          {job.traits.length > 0 ? (
            <div className="mt-2 space-y-2">
              {job.traits.map((trait) => (
                <JobTraitItem key={trait.id} trait={trait} />
              ))}
            </div>
          ) : (
            <p className="mt-2 text-slate-400">This job has no traits.</p>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}

export default JobDetailsModal;
