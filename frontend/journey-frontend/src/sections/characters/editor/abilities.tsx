import { useState } from "react";
import type { ReactNode } from "react";
import { ChevronDown, ChevronsDown, ChevronsUp, Pencil, RotateCcw, Trash2 } from "lucide-react";
import { NumberInput } from "@/components/NumberInput";
import { cn } from "@/lib/utils";
import { Modal, PrimaryButton, SecondaryButton, inputClass } from "./ui";

/** A collapsed row that opens to show details and the Del / Edit / LVL up / LVL down buttons. */
export function AbilityRow({
  title,
  summary,
  badges,
  children,
  readOnly,
  onDelete,
  onEdit,
  onLevelUp,
  onLevelDown,
  levelUpDisabled,
  levelDownDisabled,
  actions,
}: {
  title: string;
  summary: ReactNode;
  badges?: ReactNode;
  children: ReactNode;
  readOnly: boolean;
  onDelete: () => void;
  onEdit: () => void;
  onLevelUp: () => void;
  onLevelDown: () => void;
  levelUpDisabled?: string | null;
  levelDownDisabled?: string | null;
  /** Buttons beside the row (in play: spend the cost, use the trait); outside the expand button. */
  actions?: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <li className={cn("rounded-2xl border bg-white shadow-sm transition", open ? "border-violet-200" : "border-slate-200")}>
      <div className="flex items-center">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        className="flex min-w-0 flex-1 items-center gap-3 px-4 py-3 text-left"
      >
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="font-semibold text-slate-900">{title}</span>
            {badges}
          </span>
          <span className="mt-0.5 block text-sm text-slate-600">{summary}</span>
        </span>
        <ChevronDown size={18} className={cn("shrink-0 text-slate-400 transition", open && "rotate-180")} />
      </button>
      {actions && <div className="flex shrink-0 flex-col items-end gap-1 py-2 pr-3">{actions}</div>}
      </div>
      {open && (
        <div className="border-t border-slate-100 px-4 pb-3 pt-3">
          <div className="space-y-2 text-sm text-slate-700">{children}</div>
          {!readOnly && (
            <div className="mt-3 grid grid-cols-4 gap-2">
              <RowButton label="Delete" icon={<Trash2 size={16} />} onClick={onDelete} danger />
              <RowButton label="Edit" icon={<Pencil size={16} />} onClick={onEdit} />
              <RowButton label="LVL up" icon={<ChevronsUp size={18} />} onClick={onLevelUp} disabledReason={levelUpDisabled} />
              <RowButton label="LVL down" icon={<ChevronsDown size={18} />} onClick={onLevelDown} disabledReason={levelDownDisabled} />
            </div>
          )}
        </div>
      )}
    </li>
  );
}

function RowButton({
  label,
  icon,
  onClick,
  danger,
  disabledReason,
}: {
  label: string;
  icon: ReactNode;
  onClick: () => void;
  danger?: boolean;
  disabledReason?: string | null;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={Boolean(disabledReason)}
      title={disabledReason ?? label}
      className={cn(
        "flex flex-col items-center gap-0.5 rounded-xl border py-1.5 text-[11px] font-semibold transition disabled:opacity-40",
        danger ? "border-red-200 text-red-700 hover:bg-red-50" : "border-slate-200 text-slate-700 hover:bg-slate-50",
      )}
    >
      {icon}
      {label}
    </button>
  );
}

/** A labelled block of text in an expanded row; nothing when the text is empty. */
export function DetailText({ label, text }: { label: string; text: string | null | undefined }) {
  const value = text?.trim();
  if (!value || value.toLowerCase() === "none") return null;
  return (
    <div>
      <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{label}</div>
      <p className="whitespace-pre-line">{value}</p>
    </div>
  );
}

/**
 * Levelling a trait or power up or down: shows the current effect and the level-up effect, and lets the player
 * rewrite the effect for the new level. Confirming spends (or refunds) the EXP.
 */
export function LevelModal({
  name,
  direction,
  fromLevel,
  cost,
  expRemaining,
  effect,
  lvlUpEffect,
  onConfirm,
  onClose,
}: {
  name: string;
  direction: "up" | "down";
  fromLevel: number;
  cost: number;
  expRemaining: number;
  effect: string;
  lvlUpEffect: string | null;
  onConfirm: (effect: string) => void;
  onClose: () => void;
}) {
  const [text, setText] = useState(effect);
  const toLevel = direction === "up" ? fromLevel + 1 : fromLevel - 1;
  const short = direction === "up" && cost > expRemaining;
  return (
    <Modal
      title={`${name}: LVL ${fromLevel} → ${toLevel}`}
      subtitle={direction === "up" ? `Costs ${cost} EXP · ${expRemaining} EXP left` : `Refunds ${cost} EXP`}
      onClose={onClose}
      wide
      footer={
        <>
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          <PrimaryButton disabled={short} onClick={() => onConfirm(text)}>
            {direction === "up" ? `Level up for ${cost} EXP` : "Level down"}
          </PrimaryButton>
        </>
      }
    >
      <div className="space-y-3">
        {short && <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">Not enough EXP left.</p>}
        <div className="rounded-xl bg-violet-50 px-3 py-2">
          <div className="text-[11px] font-semibold uppercase tracking-wide text-violet-700">Level-up effect</div>
          <p className="whitespace-pre-line text-sm text-slate-800">{lvlUpEffect?.trim() || "This has no level-up effect listed."}</p>
        </div>
        <div className="space-y-1">
          <label htmlFor="level-effect" className="block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Effect at LVL {toLevel}
          </label>
          <textarea id="level-effect" rows={6} value={text} onChange={(event) => setText(event.target.value)} className={cn(inputClass, "resize-y")} />
          <p className="text-xs text-slate-500">Rewrite the effect to match the new level (only for this character).</p>
        </div>
      </div>
    </Modal>
  );
}

export interface EditableField {
  key: string;
  label: string;
  kind: "text" | "longtext" | "number" | "boolean" | "select";
  options?: string[];
}

/**
 * Edit notes and, under "Customize", this character's version of the trait or power. Each changed field can be
 * reset to the compendium value. {@code compendium} is null while it is being fetched.
 */
export function EditAbilityModal<F extends object>({
  name,
  notes,
  fields,
  values,
  compendium,
  edited,
  progress,
  onSave,
  onClose,
}: {
  name: string;
  notes: string;
  fields: EditableField[];
  values: F;
  compendium: F | null;
  edited: Set<string>;
  /** The character's level in it and the EXP actually spent on it (both editable, e.g. a power a trait gave free). */
  progress: { lvl: number; expSpent: number; maxLvl?: number };
  onSave: (notes: string, values: F, reset: Set<string>, progress: { lvl: number; expSpent: number }) => void;
  onClose: () => void;
}) {
  const [noteText, setNoteText] = useState(notes);
  const [lvl, setLvl] = useState(progress.lvl);
  const [expSpent, setExpSpent] = useState(progress.expSpent);
  const [draft, setDraft] = useState<F>(values);
  const [reset, setReset] = useState<Set<string>>(new Set());
  const [customizing, setCustomizing] = useState(false);
  const record = draft as Record<string, unknown>;

  const setField = (key: string, value: unknown) => {
    setDraft({ ...draft, [key]: value });
    if (reset.has(key)) {
      const next = new Set(reset);
      next.delete(key);
      setReset(next);
    }
  };

  return (
    <Modal
      title={`Edit ${name}`}
      onClose={onClose}
      wide
      footer={
        <>
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          <PrimaryButton
            onClick={() => {
              onSave(noteText, draft, reset, { lvl, expSpent });
              onClose();
            }}
          >
            Apply
          </PrimaryButton>
        </>
      }
    >
      <div className="space-y-4">
        <div className="rounded-xl border border-slate-200 p-3">
          <div className="grid grid-cols-2 gap-3">
            <label className="space-y-1">
              <span className="block text-xs font-semibold uppercase tracking-wide text-slate-500">LVL</span>
              <NumberInput min={1} max={progress.maxLvl ?? 999} value={lvl} onChange={(n) => setLvl(n ?? 1)} className={inputClass} />
            </label>
            <label className="space-y-1">
              <span className="block text-xs font-semibold uppercase tracking-wide text-slate-500">EXP spent</span>
              <NumberInput min={0} value={expSpent} onChange={(n) => setExpSpent(n ?? 0)} className={inputClass} />
            </label>
          </div>
          <p className="mt-2 text-xs text-slate-500">
            What this character actually paid. Change it when a trait or the storyteller makes it cheaper or free (e.g. a power
            from Destructive Spells), or to fix a mistake. Levelling up and down here does not add or refund EXP.
          </p>
        </div>
        <div className="space-y-1">
          <label htmlFor="ability-notes" className="block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Notes
          </label>
          <textarea
            id="ability-notes"
            rows={4}
            value={noteText}
            onChange={(event) => setNoteText(event.target.value)}
            placeholder="Choices, summons and their HP, uses left…"
            className={cn(inputClass, "resize-y")}
          />
        </div>

        <div className="rounded-xl border border-slate-200">
          <button
            type="button"
            onClick={() => setCustomizing(!customizing)}
            aria-expanded={customizing}
            className="flex w-full items-center justify-between px-3 py-2.5 text-left text-sm font-semibold text-slate-800"
          >
            <span>
              Customize for this character
              {edited.size > 0 && <span className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-[11px] text-amber-800">{edited.size} changed</span>}
            </span>
            <ChevronDown size={16} className={cn("text-slate-400 transition", customizing && "rotate-180")} />
          </button>
          {customizing && (
            <div className="space-y-3 border-t border-slate-200 px-3 py-3">
              <p className="text-xs text-slate-500">For level-ups the app does not handle, or a storyteller's ruling. Only this character changes.</p>
              {fields.map((field) => {
                const value = record[field.key];
                const isEdited = (edited.has(field.key) && !reset.has(field.key)) ||
                  (compendium !== null && JSON.stringify(value) !== JSON.stringify((compendium as Record<string, unknown>)[field.key]));
                const id = `field-${field.key}`;
                return (
                  <div key={field.key} className="space-y-1">
                    <div className="flex items-center justify-between gap-2">
                      <label htmlFor={id} className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                        {field.label}
                        {isEdited && <span className="ml-1.5 normal-case text-amber-700">· changed</span>}
                      </label>
                      {isEdited && compendium && (
                        <button
                          type="button"
                          onClick={() => {
                            setDraft({ ...draft, [field.key]: (compendium as Record<string, unknown>)[field.key] });
                            setReset(new Set(reset).add(field.key));
                          }}
                          className="flex items-center gap-1 text-xs font-medium text-violet-700 hover:text-violet-900"
                        >
                          <RotateCcw size={12} /> Reset
                        </button>
                      )}
                    </div>
                    {field.kind === "longtext" ? (
                      <textarea id={id} rows={3} value={String(value ?? "")} onChange={(e) => setField(field.key, e.target.value)} className={cn(inputClass, "resize-y")} />
                    ) : field.kind === "number" ? (
                      <NumberInput
                        id={id}
                        min={0}
                        allowEmpty
                        value={typeof value === "number" ? value : null}
                        onChange={(n) => setField(field.key, n)}
                        className={inputClass}
                      />
                    ) : field.kind === "boolean" ? (
                      <select
                        id={id}
                        value={value === null || value === undefined ? "" : String(value)}
                        onChange={(e) => setField(field.key, e.target.value === "" ? null : e.target.value === "true")}
                        className={inputClass}
                      >
                        <option value="">—</option>
                        <option value="true">Yes</option>
                        <option value="false">No</option>
                      </select>
                    ) : field.kind === "select" ? (
                      <select id={id} value={String(value ?? "")} onChange={(e) => setField(field.key, e.target.value || null)} className={inputClass}>
                        <option value="">—</option>
                        {field.options?.map((o) => (
                          <option key={o} value={o}>
                            {o}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <input id={id} value={String(value ?? "")} onChange={(e) => setField(field.key, e.target.value)} className={inputClass} />
                    )}
                  </div>
                );
              })}
              {compendium === null && <p className="text-xs text-slate-500">Loading the compendium version…</p>}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}
