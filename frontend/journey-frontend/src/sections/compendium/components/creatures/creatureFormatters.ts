import type { AbilityTiming, AugmentKind, Creature, CreatureAbility, CreatureAttack } from "../../../../models/Creature";
import { formatRaceSize } from "../races/raceFormatters";

/** Formats a creature's size or size range, e.g. "Medium" or "Small - Medium". */
export function formatCreatureSize(creature: Creature): string {
  const minSize = formatRaceSize(creature.minSize);
  return creature.maxSize ? `${minSize} - ${formatRaceSize(creature.maxSize)}` : minSize;
}

/** Section titles for each ability timing, in stat block order. */
export const ABILITY_TIMINGS: Array<{ timing: AbilityTiming; title: string }> = [
  { timing: "PASSIVE", title: "Passives" },
  { timing: "TRIGGERED", title: "Triggers" },
  { timing: "DECLARED", title: "Declared" },
];

/** Labels for each augment kind, in stat block order. */
export const AUGMENT_KINDS: Array<{ kind: AugmentKind; title: string }> = [
  { kind: "IMMUNE", title: "Immune" },
  { kind: "RESISTANT", title: "Resistant" },
  { kind: "WEAK", title: "Weak" },
];

/** Lists the costs that are set on an ability, e.g. ["31 MP", "5 TP"]. */
export function formatAbilityCosts(ability: CreatureAbility): string[] {
  return [
    ability.mpCost ? `${ability.mpCost} MP` : null,
    ability.epCost ? `${ability.epCost} EP` : null,
    ability.tpCost ? `${ability.tpCost} TP` : null,
  ].filter((cost): cost is string => cost !== null);
}

/** Formats an attack's damage, e.g. "1d8 + 12 Slashing". */
export function formatAttackDamage(attack: CreatureAttack): string {
  return `${attack.damageDice}${attack.damageBonus ? ` + ${attack.damageBonus}` : ""} ${attack.damageType.name}`;
}

/** Augment targets of one kind, e.g. "Fire, Light". */
export function augmentTargets(creature: Creature, kind: AugmentKind): string[] {
  return augmentsOfKind(creature, kind).map((augment) => augment.target);
}

/** Augments of one kind (damage types and status conditions). */
export function augmentsOfKind(creature: Creature, kind: AugmentKind): Creature["augments"] {
  return creature.augments.filter((augment) => augment.kind === kind);
}

/** Rules text shown when hovering an augment target. */
export function augmentTooltip(augment: Creature["augments"][number]): string {
  if (augment.damageType) return `${augment.damageType.name} damage: ${augment.damageType.effect}`;
  if (augment.statusCondition) return `${augment.statusCondition.name}: ${augment.statusCondition.description}`;
  return augment.target;
}
