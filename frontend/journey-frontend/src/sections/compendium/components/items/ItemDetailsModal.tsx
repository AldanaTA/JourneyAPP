import { useEffect } from "react";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import type { Item } from "../../../../models/Item";
import {
  RARITY_CLASSES,
  formatEnumLabel,
  formatItemCost,
  formatItemRarity,
  formatItemType,
  formatModifiers,
  formatWeaponDamage,
} from "./itemFormatters";

interface ItemDetailsModalProps {
  item: Item;
  isOpen: boolean;
  onClose: () => void;
}

/** A titled block of rules text inside the modal. */
function Section({ title, children, accent = "text-slate-300" }: { title: string; children: ReactNode; accent?: string }) {
  return (
    <div className="mt-5">
      <h3 className={`text-sm font-semibold uppercase tracking-wide ${accent}`}>{title}</h3>
      <div className="mt-2 leading-relaxed text-slate-200">{children}</div>
    </div>
  );
}

/** One "Label: value" line in the stats grid; hidden when there is no value. */
function Stat({ label, value }: { label: string; value: ReactNode }) {
  if (value === null || value === undefined || value === "") return null;
  return (
    <div>
      <span className="font-medium text-slate-300">{label}:</span> {value}
    </div>
  );
}

/**
 * Full-detail view of an item: its stats, effect, and the rules behind its weapon type, properties,
 * damage type, and material. It slides up as a bottom sheet on phones and is a centered dialog on
 * larger screens; it closes on backdrop tap, the close button, or the Escape key.
 */
export function ItemDetailsModal({ item, isOpen, onClose }: ItemDetailsModalProps) {
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

  const { details, weapon, material } = item;
  const modifiers = formatModifiers(item);

  // Portaled to <body> so ancestors with backdrop-filter/transform cannot trap the fixed overlay.
  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/80 backdrop-blur-sm sm:items-center sm:p-4"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="item-details-title"
        className="h-[85dvh] w-full sm:h-auto sm:max-h-[90dvh] max-w-2xl overflow-y-auto overscroll-contain rounded-t-2xl border border-slate-700 bg-slate-900 p-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] text-slate-100 shadow-2xl sm:rounded-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="mx-auto mb-3 h-1.5 w-10 rounded-full bg-slate-700 sm:hidden" aria-hidden="true" />

        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-xs uppercase tracking-[0.2em] text-violet-300">{item.isMagic ? "Magic Item" : "Item"}</p>
            <h2 id="item-details-title" className="mt-1 text-2xl font-bold text-white">{item.name}</h2>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close item details"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-slate-600 bg-slate-800 text-slate-200 hover:bg-slate-700"
            title="Close"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="h-4 w-4">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-2 text-xs text-slate-300">
          <span className={`rounded-full border px-2 py-1 font-medium ${RARITY_CLASSES[item.rarity]}`}>
            {formatItemRarity(item.rarity)}
          </span>
          <span className="rounded-full bg-slate-800 px-2 py-1">{formatItemType(item.itemType)}</span>
          {item.requiresAttunement && (
            <span className="rounded-full border border-amber-500/40 bg-amber-500/10 px-2 py-1 text-amber-200">Requires Attunement</span>
          )}
        </div>

        <div className="mt-5 grid grid-cols-1 gap-x-3 gap-y-2 text-sm text-slate-200 sm:grid-cols-2">
          <Stat label="Cost" value={formatItemCost(item)} />
          <Stat label="Stack Limit" value={item.stackLimit > 1 ? item.stackLimit : null} />
          <Stat label="Charges" value={item.charges ?? (item.isMagic ? "Passive" : null)} />
          <Stat label="Material" value={material?.name} />
          <Stat label="Based On" value={item.baseItem?.name} />
          <Stat label="Strength Req" value={details.strength_req} />
          <Stat label="Inventory Slots" value={details.inventory_slots} />
          <Stat label="Discipline" value={details.discipline ? formatEnumLabel(details.discipline) : null} />
          <Stat label="Alchemy Role" value={details.role ? formatEnumLabel(details.role) : null} />
          <Stat label="Ammunition" value={details.ammo_type ? `${formatEnumLabel(details.ammo_type)}s` : null} />
          <Stat label="Source" value={item.contentSourceName ?? "—"} />
        </div>

        {item.description && (
          <Section title="Description">
            <p className="whitespace-pre-line">{item.description}</p>
          </Section>
        )}

        {item.effect && (
          <Section title="Effect">
            <p className="whitespace-pre-line">{item.effect}</p>
          </Section>
        )}

        {modifiers && (
          <Section title="Modifiers">
            <p>{modifiers}</p>
          </Section>
        )}

        {details.skills && details.skills.length > 0 && (
          <Section title="Skill Book">
            <p>
              Choose one of <span className="font-medium text-white">{details.skills.join(", ")}</span> to raise by{" "}
              {details.skill_bonus ?? 0}% per session • {details.sessions ?? 0} session{details.sessions === 1 ? "" : "s"}
            </p>
          </Section>
        )}

        {details.magic_item_effect && (
          <Section title="Magic Item Effect" accent="text-cyan-300">
            <p className="whitespace-pre-line">{details.magic_item_effect}</p>
          </Section>
        )}

        {(details.armor_effect || details.weapon_effect) && (
          <Section title="Crafting Bonuses" accent="text-cyan-300">
            {details.armor_effect && <p><span className="font-medium text-slate-300">Armor:</span> {details.armor_effect}</p>}
            {details.weapon_effect && <p><span className="font-medium text-slate-300">Weapon:</span> {details.weapon_effect}</p>}
          </Section>
        )}

        {weapon && (
          <>
            <Section title="Weapon">
              <div className="grid grid-cols-1 gap-x-3 gap-y-2 text-sm sm:grid-cols-2">
                <Stat label="Damage" value={formatWeaponDamage(item)} />
                <Stat label="Type" value={weapon.weaponType.name} />
                <Stat label="Range" value={weapon.rangeFt ? `${weapon.rangeFt} ft` : "Melee"} />
                <Stat label="Mag" value={weapon.mag} />
              </div>
              <p className="mt-3 text-sm">
                <span className="font-medium text-cyan-300">{weapon.weaponType.effectName}:</span> {weapon.weaponType.effect}
              </p>
            </Section>

            <Section title={`${weapon.damageType.name} Damage`}>
              <p className="text-sm">{weapon.damageType.effect}</p>
              <p className="mt-1 text-xs text-slate-400">Defended with: {weapon.damageType.defenses.join(", ")}</p>
            </Section>

            {weapon.properties.length > 0 && (
              <Section title="Properties">
                <ul className="space-y-2 text-sm">
                  {weapon.properties.map((property) => (
                    <li key={property.name}>
                      <span className="font-medium text-white">{property.name}:</span> {property.description}
                    </li>
                  ))}
                </ul>
              </Section>
            )}
          </>
        )}

        {material && (material.armorEffect || material.weaponEffect) && (
          <Section title={`${material.name} Material`} accent="text-cyan-300">
            {item.itemType === "WEAPON" && material.weaponEffect && <p className="text-sm">{material.weaponEffect}</p>}
            {item.itemType !== "WEAPON" && material.armorEffect && <p className="text-sm">{material.armorEffect}</p>}
          </Section>
        )}
      </div>
    </div>,
    document.body,
  );
}

export default ItemDetailsModal;
