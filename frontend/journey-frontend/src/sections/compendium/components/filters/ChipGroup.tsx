/** Shared building blocks for the compendium filter sheets. */

export interface ChipGroupProps<T extends string> {
  label: string;
  options: T[];
  selected: T[];
  format?: (value: T) => string;
  onToggle: (value: T) => void;
}

/** A labelled group of toggleable chips, styled like the other compendium filters. */
export function ChipGroup<T extends string>({ label, options, selected, format, onToggle }: ChipGroupProps<T>) {
  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-medium text-slate-700">{label}</span>
        {selected.length > 0 && <span className="text-xs text-slate-500">{selected.length} selected</span>}
      </div>
      <div className="flex flex-wrap gap-2 rounded-xl border border-slate-200 bg-slate-50 p-2">
        {options.map((option) => {
          const isSelected = selected.includes(option);
          return (
            <button
              key={option}
              type="button"
              aria-pressed={isSelected}
              onClick={() => onToggle(option)}
              className={[
                "rounded-full border px-3 py-1.5 text-[11px] font-medium uppercase tracking-wide transition",
                isSelected
                  ? "border-violet-500 bg-violet-100 text-violet-700"
                  : "border-slate-200 bg-white text-slate-600 hover:bg-slate-100",
              ].join(" ")}
            >
              {format ? format(option) : option}
            </button>
          );
        })}
      </div>
    </div>
  );
}
