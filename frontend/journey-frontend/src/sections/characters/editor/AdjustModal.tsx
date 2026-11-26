import { useState } from "react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { signed } from "./rules";
import { Modal, PrimaryButton } from "./ui";

/** A pool shown at the top of an adjust dialog: EXP left, job points left, or a skill swap balance. */
export interface Pool {
  label: string;
  value: number;
  /** "budget": must not go below 0. "balance": must end at 0 (creation swaps). */
  kind: "budget" | "balance";
}

/**
 * The dialog behind a "±" button: the points left in big numbers (with the change since the dialog opened), the
 * value as it now is, and steppers for what can change. Changes apply as the player steps (to the draft, not the
 * server), so Done just closes.
 */
export function AdjustModal({
  title,
  value,
  valueLabel,
  pools = [],
  children,
  note,
  onClose,
}: {
  title: string;
  value: ReactNode;
  valueLabel: string;
  pools?: Pool[];
  children: ReactNode;
  note?: ReactNode;
  onClose: () => void;
}) {
  // What each pool was when the dialog opened, to show how much this dialog has used.
  const [start] = useState(() => new Map(pools.map((p) => [p.label, p.value])));
  return (
    <Modal title={title} onClose={onClose} footer={<PrimaryButton onClick={onClose}>Done</PrimaryButton>}>
      {pools.length > 0 && (
        <div className={cn("mb-3 grid gap-2", pools.length > 1 ? "grid-cols-2" : "grid-cols-1")}>
          {pools.map((pool) => {
            const delta = pool.value - (start.get(pool.label) ?? pool.value);
            const bad = pool.kind === "budget" ? pool.value < 0 : pool.value !== 0;
            const empty = pool.kind === "budget" && pool.value === 0;
            return (
              <div
                key={pool.label}
                aria-live="polite"
                className={cn(
                  "rounded-xl border-2 px-3 py-2 text-center transition-colors",
                  bad
                    ? pool.kind === "budget"
                      ? "border-red-300 bg-red-50 text-red-700"
                      : "border-amber-300 bg-amber-50 text-amber-800"
                    : empty
                      ? "border-slate-200 bg-slate-50 text-slate-500"
                      : "border-violet-300 bg-violet-50 text-violet-800",
                )}
              >
                <div className="text-[11px] font-bold uppercase tracking-wide opacity-80">{pool.label}</div>
                <div className="flex items-baseline justify-center gap-1.5">
                  <span key={pool.value} className="animate-in zoom-in-90 fade-in text-3xl font-bold tabular-nums duration-200">
                    {pool.kind === "balance" ? signed(pool.value) : pool.value}
                  </span>
                  {delta !== 0 && <span className="text-sm font-semibold tabular-nums opacity-80">({signed(delta)})</span>}
                </div>
                <div className="text-[11px] opacity-75">
                  {pool.kind === "balance" ? (pool.value === 0 ? "Balanced" : "Must end at 0") : empty ? "None left" : "left to spend"}
                </div>
              </div>
            );
          })}
        </div>
      )}
      <div className="mb-2 flex items-baseline justify-between rounded-xl bg-slate-50 px-3 py-2">
        <span className="text-sm text-slate-600">{valueLabel}</span>
        <span className="text-2xl font-semibold tabular-nums text-slate-900">{value}</span>
      </div>
      <div className="divide-y divide-slate-100">{children}</div>
      {note && <p className="mt-2 text-xs text-slate-500">{note}</p>}
    </Modal>
  );
}
