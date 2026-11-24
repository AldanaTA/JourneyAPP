import { memo } from "react";
import type { Creature } from "../../../../models/Creature";
import { augmentTargets, formatCreatureSize } from "./creatureFormatters";

interface CreatureCardProps {
  creature: Creature;
  onViewDetails: (creature: Creature) => void;
}

/**
 * Compact summary card for one creature in the virtualized list.
 * The whole card is a button so it is an easy tap target on mobile; it opens the details modal.
 */
export const CreatureCard = memo(function CreatureCard({ creature, onViewDetails }: CreatureCardProps) {
  const weakTo = augmentTargets(creature, "WEAK");

  return (
    <button
      type="button"
      onClick={() => onViewDetails(creature)}
      aria-label={`View details for ${creature.name}`}
      className="block w-full rounded-xl border border-slate-700 bg-slate-900/80 p-4 text-left text-slate-100 shadow-sm transition-colors hover:border-slate-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-500 active:bg-slate-900"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate text-lg font-semibold text-white">{creature.name}</h3>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-slate-300">
            <span className="rounded-full border border-violet-500/60 bg-violet-500/20 px-2 py-1 font-medium text-violet-100">
              Threat {creature.threatLvl}
            </span>
            <span className="rounded-full bg-slate-800 px-2 py-1">{creature.creatureType}</span>
            <span className="rounded-full bg-slate-800 px-2 py-1">{formatCreatureSize(creature)}</span>
          </div>
        </div>

        <span
          aria-hidden="true"
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-slate-600 bg-slate-800 text-slate-200"
        >
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-4 w-4">
            <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
            <circle cx="12" cy="12" r="3.2" />
          </svg>
        </span>
      </div>

      <div className="mt-4 space-y-2 text-sm text-slate-200">
        <div className="font-medium">
          HP {creature.hp} • MVM {creature.movement} ft • PRIO {creature.prio} • PD {creature.pd}
          {creature.md ? ` • MD ${creature.md}` : ""}
        </div>
        {weakTo.length > 0 && (
          <div>
            <span className="font-medium text-slate-300">Weak:</span> {weakTo.join(", ")}
          </div>
        )}
        {creature.description && <p className="line-clamp-2 text-slate-300">{creature.description}</p>}
      </div>
    </button>
  );
});

export default CreatureCard;
