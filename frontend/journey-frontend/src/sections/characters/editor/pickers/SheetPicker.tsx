import { useState } from "react";
import type { CharacterSheet } from "@/models/Character";
import type { AttributeName } from "@/models/Character";
import { useReferenceStore } from "../referenceStore";
import { ATTRIBUTE_NAMES, sheetBase } from "../rules";
import { FilterChips, PickerModal } from "./PickerModal";

const FOCUS: { value: AttributeName; label: string }[] = [
  { value: "BODY", label: "Body" },
  { value: "STRENGTH", label: "Strength" },
  { value: "SPEED", label: "Speed" },
  { value: "MAGIC", label: "Magic" },
];

/** Pick one character sheet; the focus filter keeps sheets whose highest attribute is the chosen one. */
export default function SheetPicker({
  currentId,
  onPick,
  onClose,
}: {
  currentId: string | null;
  onPick: (sheet: CharacterSheet) => void;
  onClose: () => void;
}) {
  const sheets = useReferenceStore((state) => state.sheets);
  const [focus, setFocus] = useState<AttributeName | null>(null);
  const highest = (sheet: CharacterSheet) =>
    FOCUS.reduce((best, f) => (sheetBase(sheet, f.value) > sheetBase(sheet, best) ? f.value : best), "BODY" as AttributeName);

  return (
    <PickerModal<CharacterSheet>
      title="Choose a character sheet"
      subtitle="Your starting attributes"
      searchPlaceholder="Search sheets"
      filters={<FilterChips label="Highest attribute" options={FOCUS} value={focus} onChange={setFocus} />}
      filterKey={focus ?? ""}
      load={async (page, size, search) => {
        const matches = sheets.filter(
          (s) => s.name.toLowerCase().includes(search.toLowerCase()) && (focus === null || highest(s) === focus),
        );
        return { content: matches.slice(page * size, (page + 1) * size), totalPages: Math.ceil(matches.length / size) };
      }}
      getId={(s) => s.id}
      initialSelected={sheets.filter((s) => s.id === currentId)}
      renderRow={(s) => (
        <>
          <span className="block font-semibold text-slate-900">{s.name}</span>
          <span className="mt-1 flex flex-wrap gap-1.5 text-xs text-slate-600">
            {FOCUS.map((f) => (
              <span key={f.value} className="rounded bg-slate-100 px-1.5 py-0.5 tabular-nums">
                {ATTRIBUTE_NAMES[f.value]} {sheetBase(s, f.value)}
              </span>
            ))}
          </span>
        </>
      )}
      renderDetails={(s) => (s.description ? <span className="text-sm text-slate-600">{s.description}</span> : null)}
      confirmLabel={() => "Choose sheet"}
      onConfirm={([sheet]) => {
        if (sheet) onPick(sheet);
        onClose();
      }}
      onClose={onClose}
    />
  );
}
