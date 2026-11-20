import { memo } from "react";
import type { Power } from "../../../../models/Power";

interface PowerCardProps {
  power: Power;
  onViewDetails: (power: Power) => void;
}

export const PowerCard = memo(function PowerCard({ power, onViewDetails }: PowerCardProps) {
  const cost = [
    power.mpCost !== null ? `${power.mpCost} MP` : null,
    power.tpCost !== null ? `${power.tpCost} TP` : null,
    power.hpCost !== null ? `${power.hpCost} HP` : null,
    power.epCost !== null ? `${power.epCost} EP` : null,
  ].filter(Boolean);

  return (
    <>
      <div className="rounded-xl border border-slate-700 bg-slate-900/80 p-4 text-slate-100 shadow-sm transition-colors hover:border-slate-500">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold text-white">{power.name}</h3>
            <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-slate-300">
              <span className="rounded-full bg-slate-800 px-2 py-1">LVL {power.lvl ?? "—"}</span>
              <span className="rounded-full bg-slate-800 px-2 py-1">{power.type}</span>
            </div>
          </div>

          <button
            type="button"
            onClick={() => onViewDetails(power)}
            aria-label={`View details for ${power.name}`}
            className="flex h-9 w-9 items-center justify-center rounded-md border border-slate-600 bg-slate-800 text-slate-200 transition hover:bg-slate-700"
            title="View power details"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              className="h-4 w-4"
            >
              <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
              <circle cx="12" cy="12" r="3.2" />
            </svg>
          </button>
        </div>

        <div className="mt-4 space-y-2 text-sm text-slate-200">
          <div>
            <span className="font-medium text-slate-300">Cost:</span> {cost.length > 0 ? cost.join(" • ") : "—"}
          </div>

          <div className="flex flex-wrap gap-2">
            {power.categories.length > 0 ? (
              power.categories.map((category) => (
                <span
                  key={category}
                  className="rounded-full border border-violet-500/40 bg-violet-500/10 px-2 py-1 text-[10px] font-medium uppercase tracking-wide text-violet-200"
                >
                  {category}
                </span>
              ))
            ) : (
              <span className="text-slate-400">No categories</span>
            )}
          </div>
        </div>
      </div>
    </>
  );
});

export default PowerCard;
