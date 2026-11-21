import { Inbox } from "lucide-react";

interface NoContentProps {
  /** Main message, e.g. "No content to load". */
  message?: string;
  /** Optional hint shown under the message, e.g. "Try clearing your filters." */
  hint?: string;
  /** "panel" fills an empty list; "footer" is a compact end-of-list marker. */
  variant?: "panel" | "footer";
}

/**
 * Shared empty-state used by compendium lists so the user never sees a blank area.
 * Use the panel variant when a list has no results and the footer variant once every page has loaded.
 */
export function NoContent({ message = "No content to load", hint, variant = "panel" }: NoContentProps) {
  if (variant === "footer") {
    return (
      <div className="flex items-center gap-3 py-4 text-xs font-medium uppercase tracking-[0.18em] text-slate-400" role="status">
        <span className="h-px flex-1 bg-slate-200" aria-hidden="true" />
        <span>{message}</span>
        <span className="h-px flex-1 bg-slate-200" aria-hidden="true" />
      </div>
    );
  }

  return (
    <div
      className="flex min-h-[180px] flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center"
      role="status"
    >
      <Inbox className="h-8 w-8 text-slate-400" aria-hidden="true" />
      <p className="text-sm font-medium text-slate-600">{message}</p>
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
    </div>
  );
}

export default NoContent;
