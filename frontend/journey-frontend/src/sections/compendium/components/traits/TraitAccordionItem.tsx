import { ChevronDown } from "lucide-react";
import type { Trait } from "../../../../models/Trait";
import {
  formatTraitActivationCost,
  formatTraitCategory,
  formatTraitUses,
  meaningfulText,
} from "./traitFormatters";

/**
 * One expandable trait row used inside job and race details.
 * Collapsed it shows the key numbers; expanded it shows the full rules text.
 */
export function TraitAccordionItem({ trait }: { trait: Trait }) {
  const activationCost = formatTraitActivationCost(trait);
  const traitDesc = meaningfulText(trait.traitDesc);
  const lvlUpEffect = meaningfulText(trait.lvlUpEffect);

  return (
    <details className="group rounded-xl border border-slate-700 bg-slate-800/60 open:border-slate-500">
      <summary className="flex cursor-pointer list-none items-start justify-between gap-3 p-3 [&::-webkit-details-marker]:hidden">
        <div className="min-w-0">
          <h4 className="truncate font-semibold text-white">{trait.traitName}</h4>
          <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-[11px] text-slate-300">
            <span className="rounded-full bg-slate-900 px-2 py-0.5">LVL {trait.lvl ?? "—"}</span>
            <span className="rounded-full bg-slate-900 px-2 py-0.5">Cost {trait.purchaseCost ?? "—"}</span>
            <span className="rounded-full bg-slate-900 px-2 py-0.5">{formatTraitUses(trait)}</span>
            {activationCost.length > 0 && (
              <span className="rounded-full bg-slate-900 px-2 py-0.5">{activationCost.join(" • ")}</span>
            )}
          </div>
        </div>
        <ChevronDown
          className="mt-1 h-4 w-4 shrink-0 text-slate-400 transition-transform group-open:rotate-180"
          aria-hidden="true"
        />
      </summary>

      <div className="space-y-3 border-t border-slate-700 p-3 text-sm">
        <div className="text-slate-300">
          <span className="font-medium">LVL Up Cost:</span> {trait.lvlUpCost || "—"}
        </div>

        {traitDesc && <p className="whitespace-pre-line leading-relaxed text-slate-300">{traitDesc}</p>}

        <div>
          <h5 className="text-xs font-semibold uppercase tracking-wide text-slate-300">Effect</h5>
          <p className="mt-1 whitespace-pre-line leading-relaxed text-slate-200">
            {trait.traitEffect ?? "No effect description provided."}
          </p>
        </div>

        {lvlUpEffect && (
          <div>
            <h5 className="text-xs font-semibold uppercase tracking-wide text-cyan-300">Level Up</h5>
            <p className="mt-1 whitespace-pre-line leading-relaxed text-slate-200">{lvlUpEffect}</p>
          </div>
        )}

        {trait.categories.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {trait.categories.map((category) => (
              <span
                key={category}
                className="rounded-full border border-violet-500/40 bg-violet-500/10 px-2 py-1 text-[10px] font-medium uppercase tracking-wide text-violet-200"
              >
                {formatTraitCategory(category)}
              </span>
            ))}
          </div>
        )}
      </div>
    </details>
  );
}

export default TraitAccordionItem;
