import { useId, useState } from "react";
import type { ReactNode } from "react";
import { Trash2 } from "lucide-react";
import type { ContentKind } from "@/apis/campaignApi";
import { createContent, deleteContent, updateContent } from "@/apis/campaignApi";
import { NumberInput } from "@/components/NumberInput";
import { cn } from "@/lib/utils";
import { ConfirmDialog, Modal, PrimaryButton, SecondaryButton, inputClass } from "../../characters/editor/ui";

/** What every campaign content form gets. */
export interface FormProps<T> {
  campaignId: string;
  /** The entry being edited, or null to make a new one. */
  initial: T | null;
  onDone: () => void;
  onClose: () => void;
}

/**
 * The dialog around a content form: Save (create or update through the compendium endpoints, with the campaign as
 * the content source), Delete for an existing entry, and the API's error message.
 */
export function ContentFormModal({
  kind,
  singular,
  campaignId,
  editingId,
  name,
  build,
  onDone,
  onClose,
  children,
}: {
  kind: ContentKind;
  singular: string;
  campaignId: string;
  editingId: string | null;
  name: string;
  /** The create/update request body (without content_source_id), or a message saying what is missing. */
  build: () => Record<string, unknown> | string;
  onDone: () => void;
  onClose: () => void;
  children: ReactNode;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const save = async () => {
    const body = build();
    if (typeof body === "string") {
      setError(body);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const full = { ...body, content_source_id: campaignId };
      if (editingId) await updateContent(kind, editingId, full);
      else await createContent(kind, full);
      onDone();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not save");
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!editingId) return;
    setBusy(true);
    try {
      await deleteContent(kind, editingId);
      onDone();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not delete");
      setBusy(false);
    }
  };

  return (
    <Modal
      title={editingId ? `Edit ${name || singular}` : `New ${singular}`}
      onClose={onClose}
      wide
      footer={
        <div className="flex w-full items-center justify-between gap-2">
          {editingId ? (
            <button
              type="button"
              onClick={() => setConfirmDelete(true)}
              className="flex items-center gap-1.5 rounded-lg px-2 py-2 text-sm font-semibold text-red-700 hover:bg-red-50"
            >
              <Trash2 size={15} /> Delete
            </button>
          ) : (
            <span />
          )}
          <span className="flex gap-2">
            <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
            <PrimaryButton disabled={busy} onClick={() => void save()}>
              {editingId ? "Save" : "Create"}
            </PrimaryButton>
          </span>
        </div>
      }
    >
      <div className="space-y-3">{children}</div>
      {error && <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      {confirmDelete && (
        <ConfirmDialog
          title={`Delete ${name || singular}?`}
          message="It is removed from the campaign. Characters that use it keep it until the storyteller changes them."
          confirmLabel="Delete"
          onClose={() => setConfirmDelete(false)}
          onConfirm={() => {
            setConfirmDelete(false);
            void remove();
          }}
        />
      )}
    </Modal>
  );
}

function Label({ id, children }: { id: string; children: ReactNode }) {
  return (
    <label htmlFor={id} className="block text-xs font-semibold uppercase tracking-wide text-slate-500">
      {children}
    </label>
  );
}

export function Text({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (v: string) => void; placeholder?: string }) {
  const id = useId();
  return (
    <div className="space-y-1">
      <Label id={id}>{label}</Label>
      <input id={id} value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} className={inputClass} />
    </div>
  );
}

export function Area({ label, value, onChange, rows = 3 }: { label: string; value: string; onChange: (v: string) => void; rows?: number }) {
  const id = useId();
  return (
    <div className="space-y-1">
      <Label id={id}>{label}</Label>
      <textarea id={id} rows={rows} value={value} onChange={(e) => onChange(e.target.value)} className={cn(inputClass, "resize-y")} />
    </div>
  );
}

/** A number box; empty means "not set" (null). */
export function Num({ label, value, onChange, min = 0 }: { label: string; value: number | null; onChange: (v: number | null) => void; min?: number }) {
  const id = useId();
  return (
    <div className="space-y-1">
      <Label id={id}>{label}</Label>
      <NumberInput id={id} value={value} min={min} allowEmpty onChange={onChange} className={inputClass} />
    </div>
  );
}

export function Check({ label, checked, onChange }: { label: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="h-4 w-4 accent-violet-600" />
      {label}
    </label>
  );
}

export function Select<V extends string>({
  label,
  value,
  options,
  onChange,
  allowEmpty,
}: {
  label: string;
  value: V | "";
  options: { value: V; label: string }[];
  onChange: (v: V | "") => void;
  allowEmpty?: string;
}) {
  const id = useId();
  return (
    <div className="space-y-1">
      <Label id={id}>{label}</Label>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value as V | "")} className={inputClass}>
        {allowEmpty !== undefined && <option value="">{allowEmpty}</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}

/** Toggle chips for picking several values. */
export function Chips({ label, values, options, onChange }: { label: string; values: string[]; options: { value: string; label: string }[]; onChange: (v: string[]) => void }) {
  return (
    <div className="space-y-1">
      <span className="block text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</span>
      <div className="flex flex-wrap gap-1.5">
        {options.map((o) => {
          const on = values.includes(o.value);
          return (
            <button
              key={o.value}
              type="button"
              aria-pressed={on}
              onClick={() => onChange(on ? values.filter((v) => v !== o.value) : [...values, o.value])}
              className={cn(
                "rounded-full border px-2.5 py-1 text-xs font-medium",
                on ? "border-violet-500 bg-violet-600 text-white" : "border-slate-200 bg-white text-slate-600",
              )}
            >
              {o.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/** Two or more fields side by side on wider screens. */
export function Row({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">{children}</div>;
}

