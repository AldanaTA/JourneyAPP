import { memo } from "react";
import type { Race } from "../../../../models/Race";
import { formatRaceMovement, formatRaceSizeRange, formatRaceTraitCount } from "./raceFormatters";

interface RaceCardProps {
  race: Race;
  onViewDetails: (race: Race) => void;
}

const MAX_TRAIT_PREVIEW = 6;

/**
 * Compact summary card for one race in the virtualized list.
 * The whole card is a button so it is an easy tap target on mobile; it opens the details modal.
 */
export const RaceCard = memo(function RaceCard({ race, onViewDetails }: RaceCardProps) {
  const previewTraits = race.traits.slice(0, MAX_TRAIT_PREVIEW);
  const hiddenTraitCount = race.traits.length - previewTraits.length;

  return (
    <button
      type="button"
      onClick={() => onViewDetails(race)}
      aria-label={`View details for ${race.raceName}`}
      className="block w-full rounded-xl border border-slate-700 bg-slate-900/80 p-4 text-left text-slate-100 shadow-sm transition-colors hover:border-slate-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-500 active:bg-slate-900"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate text-lg font-semibold text-white">{race.raceName}</h3>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-slate-300">
            <span className="rounded-full border border-violet-500/60 bg-violet-500/20 px-2 py-1 font-medium text-violet-100">
              {formatRaceSizeRange(race)}
            </span>
            <span className="rounded-full bg-slate-800 px-2 py-1">Move {formatRaceMovement(race)}</span>
            <span className="rounded-full bg-slate-800 px-2 py-1">{formatRaceTraitCount(race)}</span>
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
        {race.raceDescription && <p className="line-clamp-2 text-slate-300">{race.raceDescription}</p>}

        <div>
          <span className="font-medium text-slate-300">Languages:</span> {race.languages}
        </div>

        <div>
          <span className="font-medium text-slate-300">Traits:</span>{" "}
          {previewTraits.length > 0
            ? previewTraits.map((trait) => trait.traitName).join(" • ")
            : "—"}
          {hiddenTraitCount > 0 && <span className="text-slate-400"> +{hiddenTraitCount} more</span>}
        </div>
      </div>
    </button>
  );
});

export default RaceCard;
