import { useEffect } from "react";
import { createPortal } from "react-dom";
import type { Job } from "../../../../models/Job";
import { TraitAccordionItem } from "../traits/TraitAccordionItem";
import { formatJobTraitCount, totalJobPurchaseCost } from "./jobFormatters";

interface JobDetailsModalProps {
  job: Job;
  isOpen: boolean;
  onClose: () => void;
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
      className="fixed inset-0 z-[70] flex items-end justify-center bg-slate-950/80 backdrop-blur-sm sm:items-center sm:p-4"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="job-details-title"
        className="h-[85dvh] w-full sm:h-auto sm:max-h-[90dvh] max-w-2xl overflow-y-auto overscroll-contain rounded-t-2xl border border-slate-700 bg-slate-900 p-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] text-slate-100 shadow-2xl sm:rounded-2xl"
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
                <TraitAccordionItem key={trait.id} trait={trait} />
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
