import { CreatureCard } from "../../compendium/components/creatures/CreatureCard";
import { CreatureDetailsModal } from "../../compendium/components/creatures/CreatureDetailsModal";
import { ItemCard } from "../../compendium/components/items/ItemCard";
import { ItemDetailsModal } from "../../compendium/components/items/ItemDetailsModal";
import { JobCard } from "../../compendium/components/jobs/JobCard";
import { JobDetailsModal } from "../../compendium/components/jobs/JobDetailsModal";
import { PowerCard } from "../../compendium/components/powers/PowerCard";
import { PowerDetailsModal } from "../../compendium/components/powers/PowerDetailsModal";
import { RaceCard } from "../../compendium/components/races/RaceCard";
import { RaceDetailsModal } from "../../compendium/components/races/RaceDetailsModal";
import { TraitCard } from "../../compendium/components/traits/TraitCard";
import { TraitDetailsModal } from "../../compendium/components/traits/TraitDetailsModal";
import { ENTITY_TYPE_LABELS } from "./ruleLinks";
import type { RuleEntityRef, RuleEntitySelection } from "./ruleLinks";

interface RuleEntityCardProps {
  reference: RuleEntityRef;
  /** The link text from the rules body, shown when the entity no longer exists. */
  label: string;
  onOpen: (selection: RuleEntitySelection) => void;
}

/**
 * An entity shown inside rules text with the same card the compendium uses; tapping it opens the details.
 * If the entity was deleted after the chapter was imported, a muted placeholder with the original name is shown.
 */
export function RuleEntityCard({ reference, label, onOpen }: RuleEntityCardProps) {
  const card = (() => {
    switch (reference.type) {
      case "trait":
        return reference.entity && <TraitCard trait={reference.entity} onViewDetails={(entity) => onOpen({ type: "trait", id: reference.id, entity })} />;
      case "power":
        return reference.entity && <PowerCard power={reference.entity} onViewDetails={(entity) => onOpen({ type: "power", id: reference.id, entity })} />;
      case "item":
        return reference.entity && <ItemCard item={reference.entity} onViewDetails={(entity) => onOpen({ type: "item", id: reference.id, entity })} />;
      case "race":
        return reference.entity && <RaceCard race={reference.entity} onViewDetails={(entity) => onOpen({ type: "race", id: reference.id, entity })} />;
      case "job":
        return reference.entity && <JobCard job={reference.entity} onViewDetails={(entity) => onOpen({ type: "job", id: reference.id, entity })} />;
      case "creature":
        return reference.entity && <CreatureCard creature={reference.entity} onViewDetails={(entity) => onOpen({ type: "creature", id: reference.id, entity })} />;
    }
  })();

  if (!card) {
    return (
      <div className="my-4 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-4 text-sm text-slate-500">
        <span className="font-semibold text-slate-700">{label}</span> — {ENTITY_TYPE_LABELS[reference.type].toLowerCase()} not available
      </div>
    );
  }
  return <div className="my-4">{card}</div>;
}

interface RuleEntityDetailsProps {
  selection: RuleEntitySelection | null;
  onClose: () => void;
}

/** Shows the compendium details modal for whichever entity is selected in the reader. */
export function RuleEntityDetails({ selection, onClose }: RuleEntityDetailsProps) {
  if (!selection) return null;
  switch (selection.type) {
    case "trait":
      return <TraitDetailsModal trait={selection.entity} isOpen onClose={onClose} />;
    case "power":
      return <PowerDetailsModal power={selection.entity} isOpen onClose={onClose} />;
    case "item":
      return <ItemDetailsModal item={selection.entity} isOpen onClose={onClose} />;
    case "race":
      return <RaceDetailsModal race={selection.entity} isOpen onClose={onClose} />;
    case "job":
      return <JobDetailsModal job={selection.entity} isOpen onClose={onClose} />;
    case "creature":
      return <CreatureDetailsModal creature={selection.entity} isOpen onClose={onClose} />;
  }
}
