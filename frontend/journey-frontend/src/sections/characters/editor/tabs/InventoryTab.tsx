import { lazy, Suspense, useState } from "react";
import type { ReactNode } from "react";
import { Hand, Package, Pencil, Plus, Shield, ShoppingBag, Sparkles, Trash2 } from "lucide-react";
import { NumberInput } from "@/components/NumberInput";
import { cn } from "@/lib/utils";
import type { InventoryLocation } from "@/models/Character";
import type { ItemType } from "@/models/Item";
import type { DraftInventory, DraftItem, DraftSlot } from "../draft";
import { draftItem } from "../draft";
import { useDraft, useReadOnly, useUpdate } from "../hooks";
import { AddGroupModal, RenameGroupModal } from "../inventory/GroupModals";
import {
  attunedCount,
  attunementLimit,
  groupSlots,
  itemAt,
  itemInSlot,
  itemLimit,
  itemName,
  newGroupSlots,
  renameGroup,
  wealthBronze,
} from "../inventory/inventoryHelpers";
import { ItemModal } from "../inventory/ItemModal";
import { AdjustModal } from "../AdjustModal";
import { EmptyNote, Modal, Panel, PrimaryButton, SecondaryButton, Stepper, TextField } from "../ui";

const ItemPicker = lazy(() => import("../pickers/ItemPicker"));

type Picking = { title: string; location: InventoryLocation; slotKey?: string; types?: ItemType[] };
type Dialog =
  | { kind: "item"; key: string }
  | { kind: "slot"; key: string }
  | { kind: "addSlots"; group?: string }
  | { kind: "addAttunement"; group?: string }
  | { kind: "renameSlots"; group: string }
  | { kind: "renameAttunement"; group: string }
  | { kind: "limits" }
  | null;

const COINS: { key: "platinum" | "gold" | "silver" | "bronze"; label: string; short: string; color: string }[] = [
  { key: "platinum", label: "Platinum", short: "pp", color: "from-slate-200 to-slate-400 text-slate-800" },
  { key: "gold", label: "Gold", short: "gp", color: "from-amber-200 to-amber-500 text-amber-950" },
  { key: "silver", label: "Silver", short: "sp", color: "from-zinc-100 to-zinc-300 text-zinc-700" },
  { key: "bronze", label: "Bronze", short: "bp", color: "from-orange-300 to-orange-600 text-orange-950" },
];

/**
 * The inventory: money, equipped gear (armor, hands, bag), attunements, the slots the player adds and labels,
 * magic components, and quest items.
 */
export default function InventoryTab() {
  const d = useDraft();
  const readOnly = useReadOnly();
  const update = useUpdate();
  const inv = d.inventory;
  const [picking, setPicking] = useState<Picking | null>(null);
  const [dialog, setDialog] = useState<Dialog>(null);

  const changeInv = (recipe: (inventory: DraftInventory) => void) => update((x) => recipe(x.inventory));
  const slotGroups = groupSlots(inv.slots);
  const attunementGroups = groupSlots(inv.attunementSlots);
  const usedSlots = inv.slots.filter((s) => itemInSlot(inv, s.key)).length;

  const add = (item: Parameters<typeof draftItem>[0], customName: string | undefined, p: Picking) =>
    changeInv((i) => void i.items.push(draftItem(item, p.location, p.slotKey ?? null, customName)));

  return (
    <div className="space-y-4">
      {/* Purse */}
      <Panel title="Purse" action={<span className="text-xs tabular-nums text-slate-500">{formatWealth(wealthBronze(inv))}</span>}>
        <div className="grid grid-cols-4 gap-2">
          {COINS.map((coin) => (
            <label key={coin.key} className="flex flex-col items-center gap-1.5">
              <span className={cn("flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br text-[11px] font-black uppercase shadow-inner", coin.color)}>
                {coin.short}
              </span>
              <NumberInput
                min={0}
                disabled={readOnly}
                aria-label={coin.label}
                value={inv[coin.key]}
                onChange={(n) => changeInv((i) => void (i[coin.key] = n ?? 0))}
                className="w-full rounded-lg border border-slate-200 bg-white px-1 py-1.5 text-center text-base font-semibold tabular-nums outline-none focus:border-violet-400 disabled:bg-slate-50 sm:text-sm"
              />
            </label>
          ))}
        </div>
      </Panel>

      {/* Equipped */}
      <Panel title="Equipped">
        <div className="grid grid-cols-2 gap-2">
          <Place
            icon={<Shield size={18} />}
            label="Armor"
            item={itemAt(inv, "ARMOR")}
            readOnly={readOnly}
            onEmpty={() => setPicking({ title: "Wear armor", location: "ARMOR", types: ["ARMOR"] })}
            onOpen={(key) => setDialog({ kind: "item", key })}
          />
          <Place
            icon={<ShoppingBag size={18} />}
            label="Bag"
            item={itemAt(inv, "BAG")}
            readOnly={readOnly}
            onEmpty={() => setPicking({ title: "Equip a bag", location: "BAG", types: ["BAG"] })}
            onOpen={(key) => setDialog({ kind: "item", key })}
            hint={itemAt(inv, "BAG") && !readOnly ? "Add its slots below" : undefined}
          />
          {itemAt(inv, "BOTH_HANDS") ? (
            <Place
              className="col-span-2"
              icon={<Hand size={18} />}
              label="Both hands"
              item={itemAt(inv, "BOTH_HANDS")}
              readOnly={readOnly}
              onEmpty={() => undefined}
              onOpen={(key) => setDialog({ kind: "item", key })}
            />
          ) : (
            <>
              <Place
                icon={<Hand size={18} className="-scale-x-100" />}
                label="Left hand"
                item={itemAt(inv, "LEFT_HAND")}
                readOnly={readOnly}
                onEmpty={() => setPicking({ title: "Hold in the left hand", location: "LEFT_HAND" })}
                onOpen={(key) => setDialog({ kind: "item", key })}
              />
              <Place
                icon={<Hand size={18} />}
                label="Right hand"
                item={itemAt(inv, "RIGHT_HAND")}
                readOnly={readOnly}
                onEmpty={() => setPicking({ title: "Hold in the right hand", location: "RIGHT_HAND" })}
                onOpen={(key) => setDialog({ kind: "item", key })}
              />
              {!readOnly && !itemAt(inv, "LEFT_HAND") && !itemAt(inv, "RIGHT_HAND") && (
                <button
                  type="button"
                  onClick={() => setPicking({ title: "Hold in both hands", location: "BOTH_HANDS", types: ["WEAPON", "TOOL"] })}
                  className="col-span-2 text-xs font-medium text-violet-700 hover:text-violet-900"
                >
                  Hold a two-handed item
                </button>
              )}
            </>
          )}
        </div>
      </Panel>

      {/* Attunement */}
      <Panel
        title="Attunement"
        action={
          !readOnly && (
            <button type="button" onClick={() => setDialog({ kind: "addAttunement" })} className="flex items-center gap-1 text-xs font-semibold text-violet-700">
              <Plus size={14} /> Add attunements
            </button>
          )
        }
      >
        <div className="flex items-center gap-3">
          <div className="flex flex-wrap gap-1.5" aria-label={`${attunedCount(inv)} of ${attunementLimit(inv)} attunements used`}>
            {Array.from({ length: Math.max(attunementLimit(inv), attunedCount(inv)) }, (_, i) => (
              <span
                key={i}
                className={cn(
                  "flex h-7 w-7 items-center justify-center rounded-full border-2",
                  i < attunedCount(inv)
                    ? i < attunementLimit(inv)
                      ? "border-violet-500 bg-violet-500 text-white"
                      : "border-red-500 bg-red-500 text-white"
                    : "border-dashed border-slate-300",
                )}
              >
                {i < attunedCount(inv) && <Sparkles size={13} />}
              </span>
            ))}
          </div>
          <span className="ml-auto text-sm font-semibold tabular-nums text-slate-700">
            {attunedCount(inv)} / {attunementLimit(inv)}
          </span>
        </div>
        <p className="mt-2 text-xs text-slate-500">
          Base {inv.attunementLimit}
          {inv.attunementSlots.length ? ` + ${inv.attunementSlots.length} from traits and items` : ""}.{" "}
          {!readOnly && (
            <button type="button" onClick={() => setDialog({ kind: "limits" })} className="font-medium text-violet-700">
              Change limits
            </button>
          )}
        </p>
        {attunementGroups.length > 0 && (
          <ul className="mt-2 space-y-1.5">
            {attunementGroups.map(({ group, slots }) =>
              group === null ? (
                slots.map((slot) => (
                  <GroupRow
                    key={slot.key}
                    name={slot.label || "Attunement"}
                    count={1}
                    readOnly={readOnly}
                    onRemove={() => changeInv((i) => void (i.attunementSlots = i.attunementSlots.filter((s) => s.key !== slot.key)))}
                    removeBlocked={attunedCount(inv) > attunementLimit(inv) - 1 ? "End an attunement first" : null}
                  />
                ))
              ) : (
                <GroupRow
                  key={group}
                  name={group}
                  count={slots.length}
                  readOnly={readOnly}
                  onAddMore={() => setDialog({ kind: "addAttunement", group })}
                  onRename={() => setDialog({ kind: "renameAttunement", group })}
                  onRemove={() => changeInv((i) => void (i.attunementSlots = i.attunementSlots.filter((s) => s.groupName !== group)))}
                  removeBlocked={attunedCount(inv) > attunementLimit(inv) - slots.length ? "End an attunement first" : null}
                />
              ),
            )}
          </ul>
        )}
      </Panel>

      {/* Slots */}
      <Panel
        title={`Slots · ${usedSlots}/${inv.slots.length} used`}
        action={
          !readOnly && (
            <button type="button" onClick={() => setDialog({ kind: "addSlots" })} className="flex items-center gap-1 text-xs font-semibold text-violet-700">
              <Plus size={14} /> Add slots
            </button>
          )
        }
      >
        {inv.stackLimitMultiplier > 1 && (
          <p className="mb-2 rounded-lg bg-violet-50 px-3 py-1.5 text-xs font-medium text-violet-800">Stack limits ×{inv.stackLimitMultiplier}</p>
        )}
        {inv.slots.length === 0 ? (
          <EmptyNote>No slots yet. Add the slots your bag, traits, or items give, e.g. "Common Bag" × 6.</EmptyNote>
        ) : (
          <div className="space-y-3">
            {slotGroups.map(({ group, slots }) => {
              const filled = slots.some((s) => itemInSlot(inv, s.key));
              return (
                <div key={group ?? "-"} className="rounded-xl border border-slate-200">
                  <div className="flex items-center gap-2 border-b border-slate-100 bg-slate-50/70 px-3 py-1.5">
                    <span className="min-w-0 flex-1 truncate text-sm font-semibold text-slate-800">
                      {group ?? "Other slots"} <span className="font-normal text-slate-400">× {slots.length}</span>
                    </span>
                    {!readOnly && group !== null && (
                      <GroupActions
                        onAddMore={() => setDialog({ kind: "addSlots", group })}
                        onRename={() => setDialog({ kind: "renameSlots", group })}
                        onRemove={() => changeInv((i) => void (i.slots = i.slots.filter((s) => s.groupName !== group)))}
                        removeBlocked={filled ? "Empty these slots first" : null}
                      />
                    )}
                  </div>
                  <ul className="divide-y divide-slate-100">
                    {slots.map((slot) => (
                      <SlotRow
                        key={slot.key}
                        slot={slot}
                        item={itemInSlot(inv, slot.key)}
                        inv={inv}
                        readOnly={readOnly}
                        onEditSlot={() => setDialog({ kind: "slot", key: slot.key })}
                        onOpenItem={(key) => setDialog({ kind: "item", key })}
                        onStore={() => setPicking({ title: `Store in ${slot.label || "slot"}`, location: "SLOT", slotKey: slot.key })}
                      />
                    ))}
                  </ul>
                </div>
              );
            })}
          </div>
        )}
      </Panel>

      <ItemList
        title="Magic components"
        items={inv.items.filter((i) => i.location === "COMPONENTS")}
        readOnly={readOnly}
        empty="No components."
        onAdd={() => setPicking({ title: "Add a magic component", location: "COMPONENTS", types: ["ALCHEMY_INGREDIENT", "GEM", "METAL", "MISCELLANEOUS"] })}
        onOpen={(key) => setDialog({ kind: "item", key })}
      />
      <ItemList
        title="Quest items"
        items={inv.items.filter((i) => i.location === "QUEST")}
        readOnly={readOnly}
        empty="No quest items."
        onAdd={() => setPicking({ title: "Add a quest item", location: "QUEST" })}
        onOpen={(key) => setDialog({ kind: "item", key })}
      />

      <Panel title="Notes">
        <TextField label="Inventory notes" multiline disabled={readOnly} value={inv.notes} onChange={(v) => changeInv((i) => void (i.notes = v))} />
      </Panel>

      {/* Dialogs */}
      {dialog?.kind === "item" && <ItemModal itemKey={dialog.key} onClose={() => setDialog(null)} />}
      {dialog?.kind === "slot" && <SlotModal slotKey={dialog.key} onClose={() => setDialog(null)} />}
      {dialog?.kind === "addSlots" && (
        <AddGroupModal
          title="Add slots"
          initialGroup={dialog.group}
          existingGroups={slotGroups.map((g) => g.group).filter((g): g is string => g !== null)}
          onAdd={(group, count) => changeInv((i) => void i.slots.push(...newGroupSlots(i.slots, group, count)))}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === "addAttunement" && (
        <AddGroupModal
          title="Add attunements"
          initialGroup={dialog.group}
          existingGroups={attunementGroups.map((g) => g.group).filter((g): g is string => g !== null)}
          onAdd={(group, count) => changeInv((i) => void i.attunementSlots.push(...newGroupSlots(i.attunementSlots, group, count)))}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === "renameSlots" && (
        <RenameGroupModal
          group={dialog.group}
          existingGroups={slotGroups.map((g) => g.group).filter((g): g is string => g !== null)}
          onRename={(name) => changeInv((i) => renameGroup(i.slots, dialog.group, name))}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === "renameAttunement" && (
        <RenameGroupModal
          group={dialog.group}
          existingGroups={attunementGroups.map((g) => g.group).filter((g): g is string => g !== null)}
          onRename={(name) => changeInv((i) => renameGroup(i.attunementSlots, dialog.group, name))}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === "limits" && (
        <AdjustModal title="Inventory limits" valueLabel="Attunements" value={attunementLimit(inv)} onClose={() => setDialog(null)}>
          <Stepper
            label="Base attunements"
            hint="3 by the rules; traits and items add attunement slots on top"
            value={inv.attunementLimit}
            min={Math.max(0, attunedCount(inv) - inv.attunementSlots.length)}
            onChange={(v) => changeInv((i) => void (i.attunementLimit = v))}
          />
          <Stepper
            label="Stack limit multiplier"
            hint="e.g. ×2 for Supply Sergeant"
            value={inv.stackLimitMultiplier}
            min={1}
            onChange={(v) => changeInv((i) => void (i.stackLimitMultiplier = v))}
          />
        </AdjustModal>
      )}

      <Suspense fallback={null}>
        {picking && (
          <ItemPicker
            title={picking.title}
            types={picking.types}
            onPick={(item, customName) => add(item, customName, picking)}
            onClose={() => setPicking(null)}
          />
        )}
      </Suspense>
    </div>
  );
}

function formatWealth(bronze: number): string {
  const gold = Math.floor(bronze / 100);
  const rest = bronze % 100;
  return `≈ ${gold} gp${rest ? ` ${Math.floor(rest / 10)} sp ${rest % 10} bp` : ""}`;
}

/** One equipped place: the item there, or an empty tile to equip something. */
function Place({
  icon,
  label,
  item,
  readOnly,
  onEmpty,
  onOpen,
  hint,
  className,
}: {
  icon: ReactNode;
  label: string;
  item: DraftItem | undefined;
  readOnly: boolean;
  onEmpty: () => void;
  onOpen: (key: string) => void;
  hint?: string;
  className?: string;
}) {
  return (
    <button
      type="button"
      disabled={!item && readOnly}
      onClick={() => (item ? onOpen(item.key) : onEmpty())}
      className={cn(
        "flex min-h-[78px] flex-col items-start gap-1 rounded-xl border p-3 text-left transition",
        item ? "border-slate-200 bg-gradient-to-br from-white to-violet-50/60 hover:border-violet-300" : "border-dashed border-slate-300 bg-slate-50/50 hover:border-violet-300",
        className,
      )}
    >
      <span className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide text-slate-500">
        {icon} {label}
      </span>
      {item ? (
        <>
          <span className="line-clamp-2 text-sm font-semibold text-slate-900">{itemName(item)}</span>
          {(item.attuned || item.chargesRemaining !== null || hint) && (
            <span className="text-xs text-slate-500">
              {[item.attuned ? "Attuned" : null, item.chargesRemaining !== null ? `${item.chargesRemaining} charges` : null, hint].filter(Boolean).join(" · ")}
            </span>
          )}
        </>
      ) : (
        <span className="text-sm text-slate-400">{readOnly ? "Empty" : "+ Equip"}</span>
      )}
    </button>
  );
}

function GroupActions({
  onAddMore,
  onRename,
  onRemove,
  removeBlocked,
}: {
  onAddMore?: () => void;
  onRename?: () => void;
  onRemove: () => void;
  removeBlocked: string | null;
}) {
  const base = "flex h-7 w-7 items-center justify-center rounded-lg text-slate-500 hover:bg-white hover:text-slate-900 disabled:opacity-30";
  return (
    <span className="flex shrink-0 items-center">
      {onAddMore && (
        <button type="button" onClick={onAddMore} aria-label="Add more" title="Add more" className={base}>
          <Plus size={15} />
        </button>
      )}
      {onRename && (
        <button type="button" onClick={onRename} aria-label="Rename" title="Rename" className={base}>
          <Pencil size={14} />
        </button>
      )}
      <button type="button" onClick={onRemove} disabled={Boolean(removeBlocked)} aria-label="Remove" title={removeBlocked ?? "Remove"} className={base}>
        <Trash2 size={14} />
      </button>
    </span>
  );
}

function GroupRow({
  name,
  count,
  readOnly,
  onAddMore,
  onRename,
  onRemove,
  removeBlocked,
}: {
  name: string;
  count: number;
  readOnly: boolean;
  onAddMore?: () => void;
  onRename?: () => void;
  onRemove: () => void;
  removeBlocked: string | null;
}) {
  return (
    <li className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-1.5">
      <Sparkles size={14} className="text-violet-500" />
      <span className="min-w-0 flex-1 truncate text-sm text-slate-800">
        {name} {count > 1 && <span className="text-slate-400">× {count}</span>}
      </span>
      {!readOnly && <GroupActions onAddMore={onAddMore} onRename={onRename} onRemove={onRemove} removeBlocked={removeBlocked} />}
    </li>
  );
}

function SlotRow({
  slot,
  item,
  inv,
  readOnly,
  onEditSlot,
  onOpenItem,
  onStore,
}: {
  slot: DraftSlot;
  item: DraftItem | undefined;
  inv: DraftInventory;
  readOnly: boolean;
  onEditSlot: () => void;
  onOpenItem: (key: string) => void;
  onStore: () => void;
}) {
  const limit = item ? itemLimit(item, inv) : null;
  const overfull = item && limit !== null && item.quantity > limit;
  return (
    <li className="flex items-center gap-2 px-3 py-2">
      <button
        type="button"
        onClick={onEditSlot}
        disabled={readOnly}
        className="w-28 shrink-0 truncate text-left text-xs font-medium text-slate-500 hover:text-violet-700 disabled:hover:text-slate-500"
        title={slot.notes || slot.label}
      >
        {slot.label || "Unnamed"}
      </button>
      {item ? (
        <button type="button" onClick={() => onOpenItem(item.key)} className="flex min-w-0 flex-1 items-center gap-2 text-left">
          <Package size={14} className="shrink-0 text-violet-500" />
          <span className="min-w-0 flex-1 truncate text-sm font-medium text-slate-900">{itemName(item)}</span>
          <span className={cn("shrink-0 text-xs tabular-nums", overfull ? "font-semibold text-red-600" : "text-slate-500")}>
            {item.quantity}
            {limit !== null && ` / ${limit}`}
          </span>
        </button>
      ) : (
        <button
          type="button"
          onClick={onStore}
          disabled={readOnly}
          className="flex-1 text-left text-sm text-slate-400 hover:text-violet-700 disabled:hover:text-slate-400"
        >
          {readOnly ? "Empty" : "+ Store an item"}
        </button>
      )}
    </li>
  );
}

function ItemList({
  title,
  items,
  readOnly,
  empty,
  onAdd,
  onOpen,
}: {
  title: string;
  items: DraftItem[];
  readOnly: boolean;
  empty: string;
  onAdd: () => void;
  onOpen: (key: string) => void;
}) {
  return (
    <Panel
      title={title}
      action={
        !readOnly && (
          <button type="button" onClick={onAdd} className="flex items-center gap-1 text-xs font-semibold text-violet-700">
            <Plus size={14} /> Add
          </button>
        )
      }
    >
      {items.length === 0 ? (
        <p className="text-sm text-slate-400">{empty}</p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {items.map((item) => (
            <li key={item.key}>
              <button type="button" onClick={() => onOpen(item.key)} className="flex w-full items-center gap-2 py-2 text-left">
                <span className="min-w-0 flex-1 truncate text-sm font-medium text-slate-900">{itemName(item)}</span>
                <span className="text-xs tabular-nums text-slate-500">× {item.quantity}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

/** A slot's label and notes; an empty slot can be removed. */
function SlotModal({ slotKey, onClose }: { slotKey: string; onClose: () => void }) {
  const d = useDraft();
  const update = useUpdate();
  const slot = d.inventory.slots.find((s) => s.key === slotKey);
  const [label, setLabel] = useState(slot?.label ?? "");
  const [notes, setNotes] = useState(slot?.notes ?? "");
  if (!slot) return null;
  const empty = !itemInSlot(d.inventory, slotKey);
  return (
    <Modal
      title="Slot"
      subtitle={slot.groupName ? `From ${slot.groupName}` : undefined}
      onClose={onClose}
      footer={
        <div className="flex w-full items-center justify-between">
          <button
            type="button"
            disabled={!empty}
            title={empty ? "Remove slot" : "Move or remove the item first"}
            onClick={() => {
              update((x) => void (x.inventory.slots = x.inventory.slots.filter((s) => s.key !== slotKey)));
              onClose();
            }}
            className="flex items-center gap-1.5 rounded-lg px-2 py-2 text-sm font-semibold text-red-700 hover:bg-red-50 disabled:opacity-40"
          >
            <Trash2 size={15} /> Remove
          </button>
          <span className="flex gap-2">
            <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
            <PrimaryButton
              onClick={() => {
                update((x) => {
                  const s = x.inventory.slots.find((row) => row.key === slotKey);
                  if (s) {
                    s.label = label.trim();
                    s.notes = notes;
                  }
                });
                onClose();
              }}
            >
              Apply
            </PrimaryButton>
          </span>
        </div>
      }
    >
      <div className="space-y-3">
        <TextField label="Label" value={label} onChange={setLabel} />
        <TextField label="Notes" multiline value={notes} onChange={setNotes} />
      </div>
    </Modal>
  );
}
