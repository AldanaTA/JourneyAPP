import type { Power } from "../../../../models/Power";

interface PowerDetailsModalProps {
  power: Power;
  isOpen: boolean;
  onClose: () => void;
}

export function PowerDetailsModal({ power, isOpen, onClose }: PowerDetailsModalProps) {
  if (!isOpen) return null;

  const cost = [
    power.mpCost !== null ? `${power.mpCost} MP` : null,
    power.tpCost !== null ? `${power.tpCost} TP` : null,
    power.hpCost !== null ? `${power.hpCost} HP` : null,
    power.epCost !== null ? `${power.epCost} EP` : null,
  ].filter(Boolean);

  const componentString = [
    power.verbal ? "V" : null,
    power.sight ? "O" : null,
    power.somatic ? "S" : null,
  ].filter(Boolean).join(", ") || "—";

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl border border-slate-700 bg-slate-900 p-5 text-slate-100 shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-[0.2em] text-violet-300">Power</p>
            <h2 className="mt-1 text-2xl font-bold text-white">{power.name}</h2>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close power details"
            className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-600 bg-slate-800 text-slate-200 hover:bg-slate-700"
            title="Close"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              className="h-4 w-4"
            >
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-2 text-xs text-slate-300">
          <span className="rounded-full bg-slate-800 px-2 py-1">LVL {power.lvl ?? "—"}</span>
          <span className="rounded-full bg-slate-800 px-2 py-1">{power.type}</span>
          <span className="rounded-full bg-slate-800 px-2 py-1">{cost.length > 0 ? cost.join(" • ") : "—"}</span>
        </div>

        <div className="mt-5 grid gap-3 text-sm text-slate-200 sm:grid-cols-2">
          <div>
            <span className="font-medium text-slate-300">Range:</span> {power.range ?? "—"}
          </div>
          <div>
            <span className="font-medium text-slate-300">Area:</span> {power.area ?? "—"}
          </div>
          <div>
            <span className="font-medium text-slate-300">Duration:</span> {power.duration ?? "—"}
          </div>
          <div>
            <span className="font-medium text-slate-300">Components:</span> {componentString}
          </div>
          <div>
            <span className="font-medium text-slate-300">Material:</span> {power.materialComponents ?? "—"}
          </div>
          <div>
            <span className="font-medium text-slate-300">Source:</span> {power.contentSourceName ?? "—"}
          </div>
        </div>

        <div className="mt-5">
          <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-300">Effect</h3>
          <p className="mt-2 leading-relaxed text-slate-200">{power.effect ?? "No effect description provided."}</p>
        </div>

        {(power.empowerEffect || power.lvlUpEffect) && (
          <div className="mt-5 space-y-4">
            {power.empowerEffect && (
              <div>
                <h3 className="text-sm font-semibold uppercase tracking-wide text-amber-300">Empower</h3>
                <p className="mt-2 leading-relaxed text-slate-200">{power.empowerEffect}</p>
              </div>
            )}

            {power.lvlUpEffect && (
              <div>
                <h3 className="text-sm font-semibold uppercase tracking-wide text-cyan-300">Level Up</h3>
                <p className="mt-2 leading-relaxed text-slate-200">{power.lvlUpEffect}</p>
              </div>
            )}
          </div>
        )}

        <div className="mt-6 flex flex-wrap gap-2">
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
  );
}

export default PowerDetailsModal;
