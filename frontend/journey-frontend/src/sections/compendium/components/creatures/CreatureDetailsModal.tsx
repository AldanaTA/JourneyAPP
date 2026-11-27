import { useEffect } from "react";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import { ChevronDown } from "lucide-react";
import type { Creature, CreatureAbility } from "../../../../models/Creature";
import { TraitAccordionItem } from "../traits/TraitAccordionItem";
import {
  ABILITY_TIMINGS,
  AUGMENT_KINDS,
  augmentTooltip,
  augmentsOfKind,
  formatAbilityCosts,
  formatAttackDamage,
  formatCreatureSize,
} from "./creatureFormatters";

interface CreatureDetailsModalProps {
  creature: Creature;
  isOpen: boolean;
  onClose: () => void;
}

/** A titled block inside the modal. */
function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="mt-5">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-300">{title}</h3>
      <div className="mt-2 leading-relaxed text-slate-200">{children}</div>
    </div>
  );
}

/** One ability: the creature's own text, plus the full trait or power it comes from (expandable). */
function AbilityItem({ ability }: { ability: CreatureAbility }) {
  const costs = formatAbilityCosts(ability);
  const chips = [
    ability.lvl ? `LVL ${ability.lvl}` : null,
    ability.uses ? `${ability.uses} use${ability.uses === 1 ? "" : "s"}` : null,
    ...costs,
  ].filter((chip): chip is string => chip !== null);

  return (
    <div className="rounded-xl border border-slate-700 bg-slate-800/60 p-3">
      <div className="flex flex-wrap items-center gap-2">
        <h4 className="font-semibold text-white">{ability.name}</h4>
        {chips.map((chip) => (
          <span key={chip} className="rounded-full bg-slate-900 px-2 py-0.5 text-[11px] text-slate-300">{chip}</span>
        ))}
      </div>
      <p className="mt-1.5 whitespace-pre-line text-sm leading-relaxed text-slate-200">{ability.effect}</p>

      {ability.trait && (
        <div className="mt-3 space-y-1.5">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-cyan-300">Full trait</p>
          <TraitAccordionItem trait={ability.trait} />
        </div>
      )}

      {ability.power && (
        <details className="group mt-3 rounded-xl border border-slate-700 bg-slate-900/60 open:border-slate-500">
          <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-3 [&::-webkit-details-marker]:hidden">
            <span className="text-sm">
              <span className="text-[11px] font-semibold uppercase tracking-wide text-cyan-300">Full power </span>
              <span className="font-semibold text-white">{ability.power.name}</span>
            </span>
            <ChevronDown className="h-4 w-4 shrink-0 text-slate-400 transition-transform group-open:rotate-180" aria-hidden="true" />
          </summary>
          <div className="space-y-2 border-t border-slate-700 p-3 text-sm text-slate-200">
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-slate-300">
              {ability.power.range && <span>Range: {ability.power.range}</span>}
              {ability.power.area && <span>Area: {ability.power.area}</span>}
              {ability.power.duration && <span>Duration: {ability.power.duration}</span>}
            </div>
            {ability.power.effect && <p className="whitespace-pre-line leading-relaxed">{ability.power.effect}</p>}
            {ability.power.empowerEffect && (
              <p className="whitespace-pre-line leading-relaxed">
                <span className="font-medium text-cyan-300">Empower:</span> {ability.power.empowerEffect}
              </p>
            )}
          </div>
        </details>
      )}
    </div>
  );
}

/**
 * Full stat block of a creature. It slides up as a bottom sheet on phones and is a centered dialog on
 * larger screens; it closes on backdrop tap, the close button, or the Escape key.
 */
export function CreatureDetailsModal({ creature, isOpen, onClose }: CreatureDetailsModalProps) {
  /** Closes the modal when Escape is pressed and locks background scrolling while it is open. */
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const stats: Array<[string, string | number | null]> = [
    ["HP", creature.hp],
    ["WP", creature.wp],
    ["MP", creature.mp],
    ["EP", creature.ep],
    ["PRIO", creature.prio],
    ["MVM", `${creature.movement} ft`],
    ["PD", creature.pd],
    ["MD", creature.md],
  ];
  const augmentRows = AUGMENT_KINDS
    .map(({ kind, title }) => ({ title, augments: augmentsOfKind(creature, kind) }))
    .filter((row) => row.augments.length > 0);

  // Portaled to <body> so ancestors with backdrop-filter/transform cannot trap the fixed overlay.
  return createPortal(
    <div
      className="fixed inset-0 z-[70] flex items-end justify-center bg-slate-950/80 backdrop-blur-sm sm:items-center sm:p-4"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="creature-details-title"
        className="h-[85dvh] w-full sm:h-auto sm:max-h-[90dvh] max-w-2xl overflow-y-auto overscroll-contain rounded-t-2xl border border-slate-700 bg-slate-900 p-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] text-slate-100 shadow-2xl sm:rounded-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="mx-auto mb-3 h-1.5 w-10 rounded-full bg-slate-700 sm:hidden" aria-hidden="true" />

        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-xs uppercase tracking-[0.2em] text-violet-300">Creature</p>
            <h2 id="creature-details-title" className="mt-1 text-2xl font-bold text-white">{creature.name}</h2>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close creature details"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-slate-600 bg-slate-800 text-slate-200 hover:bg-slate-700"
            title="Close"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="h-4 w-4">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-2 text-xs text-slate-300">
          <span className="rounded-full border border-violet-500/60 bg-violet-500/20 px-2 py-1 font-medium text-violet-100">
            Threat {creature.threatLvl}
          </span>
          <span className="rounded-full bg-slate-800 px-2 py-1">{creature.creatureType}</span>
          <span className="rounded-full bg-slate-800 px-2 py-1">{formatCreatureSize(creature)}</span>
        </div>

        <div className="mt-5 grid grid-cols-4 gap-2 text-center text-sm">
          {stats.filter(([, value]) => value !== null).map(([label, value]) => (
            <div key={label} className="rounded-lg border border-slate-700 bg-slate-800/60 px-2 py-1.5">
              <div className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">{label}</div>
              <div className="font-semibold text-white">{value}</div>
            </div>
          ))}
        </div>

        <div className="mt-4 grid grid-cols-1 gap-x-3 gap-y-1 text-sm text-slate-200 sm:grid-cols-2">
          {creature.languages && (
            <div><span className="font-medium text-slate-300">Languages:</span> {creature.languages}</div>
          )}
          <div><span className="font-medium text-slate-300">Source:</span> {creature.contentSourceName ?? "—"}</div>
        </div>

        {creature.description && (
          <Section title="Description">
            <p className="whitespace-pre-line">{creature.description}</p>
          </Section>
        )}

        {creature.lore && (
          <Section title="Lore">
            <p className="whitespace-pre-line">{creature.lore}</p>
          </Section>
        )}

        {creature.skills.length > 0 && (
          <Section title="Skills">
            <p className="text-sm">{creature.skills.map(({ skill, rating }) => `${skill} ${rating}%`).join(", ")}</p>
            <p className="mt-1 text-xs text-slate-400">Skills not listed are 40%.</p>
          </Section>
        )}

        {augmentRows.length > 0 && (
          <Section title="Augments">
            <div className="space-y-1 text-sm">
              {augmentRows.map(({ title, augments }) => (
                <div key={title} className="flex flex-wrap items-center gap-1.5">
                  <span className="font-medium text-slate-300">{title}:</span>
                  {augments.map((augment) => (
                    <span
                      key={augment.target}
                      title={augmentTooltip(augment)}
                      className={[
                        "cursor-help rounded-full border px-2 py-0.5 text-xs",
                        augment.statusCondition
                          ? "border-amber-500/40 bg-amber-500/10 text-amber-200"
                          : "border-slate-600 bg-slate-800 text-slate-200",
                      ].join(" ")}
                    >
                      {augment.target}
                    </span>
                  ))}
                </div>
              ))}
            </div>
            <p className="mt-2 text-xs text-slate-400">Hover a tag to see its rules. Status conditions are highlighted.</p>
          </Section>
        )}

        {ABILITY_TIMINGS.map(({ timing, title }) => {
          const abilities = creature.abilities.filter((ability) => ability.timing === timing);
          const attacks = timing === "DECLARED" ? creature.attacks : [];
          if (abilities.length === 0 && attacks.length === 0) return null;

          return (
            <Section key={timing} title={title}>
              <div className="space-y-2">
                {attacks.map((attack) => (
                  <div key={attack.name} className="rounded-xl border border-slate-700 bg-slate-800/60 p-3 text-sm">
                    <div className="flex flex-wrap items-center gap-2">
                      <h4 className="font-semibold text-white">{attack.name}</h4>
                      <span className="rounded-full bg-slate-900 px-2 py-0.5 text-[11px] text-slate-300">Attack</span>
                    </div>
                    <p className="mt-1.5 text-slate-200">
                      {attack.skill}
                      {attack.rangeFt ? ` • ${attack.rangeFt} ft` : ""} • {formatAttackDamage(attack)}
                      {attack.mag ? ` • Mag ${attack.mag}` : ""}
                    </p>
                    {attack.properties.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {attack.properties.map((property) => (
                          <span
                            key={property.name}
                            title={property.description}
                            className="rounded-full border border-violet-500/40 bg-violet-500/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-violet-200"
                          >
                            {property.name}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
                {abilities.map((ability) => (
                  <AbilityItem key={`${ability.timing}-${ability.name}`} ability={ability} />
                ))}
              </div>
            </Section>
          );
        })}
      </div>
    </div>,
    document.body,
  );
}

export default CreatureDetailsModal;
