import type { InputHTMLAttributes, ReactNode } from "react";
import { Spinner } from "@/components/ui/spinner";
import { inputClass } from "./formStyles";

/** Page title block used by every account page. */
export function AuthPageHeader({ title, eyebrow = "Account" }: { title: string; eyebrow?: string }) {
  return (
    <header className="space-y-1 border-b border-slate-200 pb-3">
      <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">{eyebrow}</p>
      <h2 className="text-2xl font-semibold tracking-tight text-slate-900 md:text-3xl">{title}</h2>
    </header>
  );
}

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  id: string;
  label: ReactNode;
  hint?: ReactNode;
  /** Shown in red under the field (and marks it invalid for screen readers). */
  problem?: string | null;
}

/** A labelled input with an optional hint or problem message. */
export function Field({ id, label, hint, problem, className, ...input }: FieldProps) {
  const describedBy = problem ? `${id}-problem` : hint ? `${id}-hint` : undefined;
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">{label}</label>
      <input
        id={id}
        aria-invalid={problem ? true : undefined}
        aria-describedby={describedBy}
        className={`${inputClass} ${problem ? "border-red-300 focus:border-red-400 focus:ring-red-100" : ""} ${className ?? ""}`}
        {...input}
      />
      {problem ? (
        <p id={`${id}-problem`} className="text-xs text-red-600">{problem}</p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-xs text-slate-500">{hint}</p>
      ) : null}
    </div>
  );
}

/** Red message box for a failed request. */
export function ErrorBox({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
      {message}
    </p>
  );
}

/** Green message box for a finished action. */
export function NoticeBox({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="status" className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
      {message}
    </p>
  );
}

/** Full-width submit button with a spinner while busy. */
export function SubmitButton({ busy, icon, children }: { busy: boolean; icon?: ReactNode; children: ReactNode }) {
  return (
    <button
      type="submit"
      disabled={busy}
      className="flex w-full items-center justify-center gap-2 rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-60"
    >
      {busy ? <Spinner className="h-4 w-4" /> : icon}
      {children}
    </button>
  );
}
