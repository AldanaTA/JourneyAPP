import { useEffect, useId, useState } from "react";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import { ChevronDown, Minus, Plus, X } from "lucide-react";
import { NumberInput } from "@/components/NumberInput";
import { cn } from "@/lib/utils";

export const inputClass =
  "w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-base text-slate-900 outline-none " +
  "placeholder:text-slate-400 focus:border-violet-400 focus:ring-2 focus:ring-violet-100 disabled:bg-slate-50 " +
  "disabled:text-slate-500 sm:text-sm";

/**
 * A dialog: a bottom sheet on phones, centred on larger screens. Closes on the backdrop, the X, or Escape.
 * Portaled to <body> so it sits above the editor.
 */
export function Modal({
  title,
  subtitle,
  onClose,
  children,
  footer,
  wide = false,
  tall = false,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
  /** A fixed height, for dialogs whose content changes size (search results), so they do not jump. */
  tall?: boolean;
}) {
  const titleId = useId();
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return createPortal(
    <div
      className="fixed inset-0 z-[60] flex items-end justify-center bg-slate-950/60 backdrop-blur-[2px] sm:items-center sm:p-4"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(event) => event.stopPropagation()}
        className={cn(
          "flex max-h-[92dvh] w-full flex-col rounded-t-2xl bg-[#fffdfb] text-slate-900 shadow-2xl sm:rounded-2xl",
          wide ? "sm:max-w-2xl" : "sm:max-w-md",
          tall && "h-[92dvh] sm:h-[80dvh]",
        )}
      >
        <div className="flex items-start justify-between gap-3 border-b border-slate-200 px-5 pb-3 pt-4">
          <div className="min-w-0">
            <h2 id={titleId} className="truncate text-lg font-semibold">
              {title}
            </h2>
            {subtitle && <div className="mt-0.5 text-sm text-slate-500">{subtitle}</div>}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="-mr-1 rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-900"
          >
            <X size={18} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto overscroll-contain px-5 py-4">{children}</div>
        {footer && (
          <div className="flex flex-wrap justify-end gap-2 border-t border-slate-200 px-5 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
            {footer}
          </div>
        )}
      </div>
    </div>,
    document.body,
  );
}

/** A titled box on a tab; {@code collapsible} lets the player fold it away with its title. */
export function Panel({
  title,
  action,
  children,
  className,
  collapsible = false,
  defaultOpen = true,
}: {
  title: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  collapsible?: boolean;
  defaultOpen?: boolean;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const shown = !collapsible || open;
  const heading = <h3 className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-700">{title}</h3>;
  return (
    <section className={cn("rounded-2xl border border-slate-200 bg-white p-4 shadow-sm", className)}>
      <div className={cn("flex items-center justify-between gap-2", shown && "mb-3")}>
        {collapsible ? (
          <button type="button" onClick={() => setOpen(!open)} aria-expanded={open} className="-my-1 flex flex-1 items-center gap-1.5 py-1 text-left">
            <ChevronDown size={16} className={cn("shrink-0 text-violet-500 transition", !open && "-rotate-90")} />
            {heading}
          </button>
        ) : (
          heading
        )}
        {action}
      </div>
      {shown && children}
    </section>
  );
}

/** The round "±" button next to a value; opens that value's adjust dialog. */
export function AdjustButton({ onClick, label, disabled }: { onClick: () => void; label: string; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      title={label}
      className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-violet-200 bg-violet-50 text-sm font-bold text-violet-700 transition hover:bg-violet-100 disabled:invisible"
    >
      ±
    </button>
  );
}

/** A small labelled number in a value row ("5 EXP", "+1 Bonus"). */
export function Chip({ label, value, tone = "slate" }: { label: string; value: ReactNode; tone?: "slate" | "violet" | "amber" }) {
  const tones = {
    slate: "bg-slate-100 text-slate-600",
    violet: "bg-violet-100 text-violet-700",
    amber: "bg-amber-100 text-amber-800",
  };
  return (
    <span className={cn("inline-flex items-baseline gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-medium tabular-nums", tones[tone])}>
      <span className="font-semibold">{value}</span>
      <span className="opacity-75">{label}</span>
    </span>
  );
}

/**
 * One row of values: "Body  44  [5 EXP] [+1 Bonus]  ±". {@code chips} are shown under the name on phones and
 * beside it on wider screens. {@code onTap} makes the name and value a button (e.g. roll the skill in play).
 */
export function ValueRow({
  name,
  value,
  chips,
  onAdjust,
  readOnly,
  onTap,
  tapLabel,
}: {
  name: ReactNode;
  value: ReactNode;
  chips?: ReactNode;
  onAdjust?: () => void;
  readOnly?: boolean;
  onTap?: () => void;
  tapLabel?: string;
}) {
  const body = (
    <>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span className="text-sm font-medium text-slate-800">{name}</span>
          {chips && <span className="flex flex-wrap gap-1">{chips}</span>}
        </div>
      </div>
      <span className="min-w-[3ch] text-right text-lg font-semibold tabular-nums text-slate-900">{value}</span>
    </>
  );
  return (
    <div className="flex items-center gap-3 border-b border-slate-100 py-2 last:border-b-0">
      {onTap ? (
        <button
          type="button"
          onClick={onTap}
          aria-label={tapLabel}
          title={tapLabel}
          className="-mx-1.5 flex min-w-0 flex-1 items-center gap-3 rounded-lg px-1.5 py-0.5 text-left transition hover:bg-violet-50 active:bg-violet-100"
        >
          {body}
        </button>
      ) : (
        body
      )}
      {onAdjust && !readOnly ? <AdjustButton onClick={onAdjust} label={`Adjust ${typeof name === "string" ? name : ""}`} /> : <span className="w-8" />}
    </div>
  );
}

/** A number with − and + buttons, used in the adjust dialogs. */
export function Stepper({
  label,
  hint,
  value,
  onChange,
  min = Number.MIN_SAFE_INTEGER,
  max = Number.MAX_SAFE_INTEGER,
  step = 1,
}: {
  label: ReactNode;
  hint?: ReactNode;
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  step?: number;
}) {
  const id = useId();
  const clamp = (n: number) => Math.min(max, Math.max(min, n));
  return (
    <div className="flex items-center justify-between gap-3 py-2">
      <label htmlFor={id} className="min-w-0">
        <span className="block text-sm font-medium text-slate-800">{label}</span>
        {hint && <span className="block text-xs text-slate-500">{hint}</span>}
      </label>
      <div className="flex shrink-0 items-center gap-1">
        <button
          type="button"
          aria-label="Decrease"
          onClick={() => onChange(clamp(value - step))}
          disabled={value - step < min}
          className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 disabled:opacity-40"
        >
          <Minus size={16} />
        </button>
        <NumberInput
          id={id}
          value={value}
          min={min}
          max={max}
          onChange={(n) => onChange(clamp(n ?? 0))}
          className="h-9 w-16 rounded-lg border border-slate-200 bg-white text-center text-base font-semibold tabular-nums outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100 sm:text-sm"
        />
        <button
          type="button"
          aria-label="Increase"
          onClick={() => onChange(clamp(value + step))}
          disabled={value + step > max}
          className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 disabled:opacity-40"
        >
          <Plus size={16} />
        </button>
      </div>
    </div>
  );
}

/** A labelled text input or text area. */
export function TextField({
  label,
  value,
  onChange,
  multiline = false,
  placeholder,
  disabled,
  rows = 3,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  multiline?: boolean;
  placeholder?: string;
  disabled?: boolean;
  rows?: number;
}) {
  const id = useId();
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="block text-xs font-semibold uppercase tracking-wide text-slate-500">
        {label}
      </label>
      {multiline ? (
        <textarea
          id={id}
          rows={rows}
          value={value}
          placeholder={placeholder}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
          className={cn(inputClass, "resize-y")}
        />
      ) : (
        <input
          id={id}
          value={value}
          placeholder={placeholder}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
          className={inputClass}
        />
      )}
    </div>
  );
}

export function PrimaryButton({ children, className, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      {...props}
      className={cn(
        "inline-flex items-center justify-center gap-1.5 rounded-lg bg-violet-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-violet-700 disabled:opacity-50",
        className,
      )}
    >
      {children}
    </button>
  );
}

export function SecondaryButton({ children, className, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      {...props}
      className={cn(
        "inline-flex items-center justify-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3.5 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-50",
        className,
      )}
    >
      {children}
    </button>
  );
}

/** The round floating "+" button. */
export function Fab({
  onClick,
  label,
  side = "right",
  className,
}: {
  onClick: () => void;
  label: string;
  side?: "left" | "right";
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className={cn(
        "fixed z-30 flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-violet-500 to-indigo-600 text-white shadow-lg shadow-violet-500/40 transition hover:scale-105 active:scale-95",
        side === "right" ? "right-5" : "left-5",
        className,
      )}
    >
      <Plus size={26} strokeWidth={2.5} />
    </button>
  );
}

/** "Are you sure?" with one destructive choice. */
export function ConfirmDialog({
  title,
  message,
  confirmLabel,
  onConfirm,
  onClose,
}: {
  title: string;
  message: ReactNode;
  confirmLabel: string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Modal
      title={title}
      onClose={onClose}
      footer={
        <>
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          <button
            type="button"
            onClick={onConfirm}
            className="rounded-lg bg-red-600 px-3.5 py-2 text-sm font-semibold text-white hover:bg-red-700"
          >
            {confirmLabel}
          </button>
        </>
      }
    >
      <div className="text-sm text-slate-600">{message}</div>
    </Modal>
  );
}

/** A short note when a list is empty. */
export function EmptyNote({ children }: { children: ReactNode }) {
  return <p className="rounded-xl border border-dashed border-slate-200 px-3 py-4 text-center text-sm text-slate-500">{children}</p>;
}
