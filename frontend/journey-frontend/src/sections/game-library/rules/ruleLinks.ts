import type { Creature } from "../../../models/Creature";
import type { Item } from "../../../models/Item";
import type { Job } from "../../../models/Job";
import type { Power } from "../../../models/Power";
import type { Race } from "../../../models/Race";
import type { RuleChapter, RuleEntityType } from "../../../models/Rules";
import type { Trait } from "../../../models/Trait";

/** An entity a rules body links to, with its loaded data (null when it no longer exists). */
export type RuleEntityRef =
  | { type: "trait"; id: string; entity: Trait | null }
  | { type: "power"; id: string; entity: Power | null }
  | { type: "item"; id: string; entity: Item | null }
  | { type: "race"; id: string; entity: Race | null }
  | { type: "job"; id: string; entity: Job | null }
  | { type: "creature"; id: string; entity: Creature | null };

/** A loaded entity ready to show in a details modal. */
export type RuleEntitySelection =
  | { type: "trait"; id: string; entity: Trait }
  | { type: "power"; id: string; entity: Power }
  | { type: "item"; id: string; entity: Item }
  | { type: "race"; id: string; entity: Race }
  | { type: "job"; id: string; entity: Job }
  | { type: "creature"; id: string; entity: Creature };

const ENTITY_LINK = /^(trait|power|item|race|job|creature):([0-9a-f-]{36})$/i;
const ASSET_LINK = /^asset:([0-9a-f-]{36})$/i;
const CUSTOM_SCHEME = /^(trait|power|item|race|job|creature|asset):/i;

/** Splits an entity link target such as "trait:<uuid>"; null for any other URL. */
export function parseEntityLink(href: string | undefined | null): { type: RuleEntityType; id: string } | null {
  const match = href ? ENTITY_LINK.exec(href) : null;
  return match ? { type: match[1].toLowerCase() as RuleEntityType, id: match[2].toLowerCase() } : null;
}

/** Returns the asset id of an image source such as "asset:<uuid>"; null for any other URL. */
export function parseAssetLink(src: string | undefined | null): string | null {
  const match = src ? ASSET_LINK.exec(src) : null;
  return match ? match[1].toLowerCase() : null;
}

/** True for the custom link schemes rules bodies use, which the Markdown renderer must keep. */
export function isRuleScheme(url: string): boolean {
  return CUSTOM_SCHEME.test(url);
}

/** Looks up the entity a link points at in the chapter's reference maps. */
export function resolveEntity(chapter: RuleChapter, type: RuleEntityType, id: string): RuleEntityRef {
  switch (type) {
    case "trait":
      return { type, id, entity: chapter.traits[id] ?? null };
    case "power":
      return { type, id, entity: chapter.powers[id] ?? null };
    case "item":
      return { type, id, entity: chapter.items[id] ?? null };
    case "race":
      return { type, id, entity: chapter.races[id] ?? null };
    case "job":
      return { type, id, entity: chapter.jobs[id] ?? null };
    case "creature":
      return { type, id, entity: chapter.creatures[id] ?? null };
  }
}

/** Display label for an entity type, e.g. for "trait not available". */
export const ENTITY_TYPE_LABELS: Record<RuleEntityType, string> = {
  trait: "Trait",
  power: "Power",
  item: "Item",
  race: "Race",
  job: "Job",
  creature: "Creature",
};

/** Element id used for a section's anchor (prefixed so slugs cannot clash with other ids on the page). */
export function sectionAnchorId(slug: string): string {
  return `section-${slug}`;
}
