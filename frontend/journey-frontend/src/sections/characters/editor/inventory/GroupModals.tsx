import { useState } from "react";
import { Modal, PrimaryButton, SecondaryButton, Stepper, TextField } from "../ui";

/**
 * Adds slots together: a trait or bag that gives 3 slots becomes "Spatial Storage 1..3". Adding to an existing
 * group carries on its numbering.
 */
export function AddGroupModal({
  title,
  existingGroups,
  initialGroup = "",
  onAdd,
  onClose,
}: {
  title: string;
  existingGroups: string[];
  initialGroup?: string;
  onAdd: (group: string, count: number) => void;
  onClose: () => void;
}) {
  const [group, setGroup] = useState(initialGroup);
  const [count, setCount] = useState(1);
  const name = group.trim();
  return (
    <Modal
      title={title}
      subtitle="Labelled by where they come from, numbered 1, 2, 3…"
      onClose={onClose}
      footer={
        <>
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          <PrimaryButton
            disabled={!name}
            onClick={() => {
              onAdd(name, count);
              onClose();
            }}
          >
            Add {count}
          </PrimaryButton>
        </>
      }
    >
      <div className="space-y-3">
        <TextField label="From (trait, bag, item…)" value={group} placeholder="Spatial Storage" onChange={setGroup} />
        {existingGroups.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {existingGroups.map((g) => (
              <button
                key={g}
                type="button"
                onClick={() => setGroup(g)}
                className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-600 hover:border-violet-300"
              >
                {g}
              </button>
            ))}
          </div>
        )}
        <Stepper label="How many" value={count} min={1} max={50} onChange={setCount} />
        {name && (
          <p className="text-xs text-slate-500">
            {existingGroups.includes(name) ? `Adds to ${name}, numbered on from its last slot.` : `Makes ${name} 1${count > 1 ? ` … ${name} ${count}` : ""}.`}
          </p>
        )}
      </div>
    </Modal>
  );
}

/** Renames a group; numbered labels follow ("Old 2" → "New 2"). */
export function RenameGroupModal({
  group,
  existingGroups,
  onRename,
  onClose,
}: {
  group: string;
  existingGroups: string[];
  onRename: (name: string) => void;
  onClose: () => void;
}) {
  const [name, setName] = useState(group);
  const clean = name.trim();
  const taken = clean !== group && existingGroups.includes(clean);
  return (
    <Modal
      title={`Rename ${group}`}
      onClose={onClose}
      footer={
        <>
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          <PrimaryButton
            disabled={!clean || taken || clean === group}
            onClick={() => {
              onRename(clean);
              onClose();
            }}
          >
            Rename
          </PrimaryButton>
        </>
      }
    >
      <TextField label="New name" value={name} onChange={setName} />
      {taken && <p className="mt-2 text-sm text-amber-700">There is already a group called {clean}.</p>}
    </Modal>
  );
}
