import { useState } from "react";
import type { CharacterSheet } from "@/models/Character";
import type { AttributeName } from "@/models/Character";
import { useSheetOptions } from "../hooks";
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
  // The official sheets, then (for a campaign character) the campaign's own.
  const options = useSheetOptions();
  const sheets = [...options.official, ...options.campaign];
  const campaignIds = new Set(options.campaign.map((s) => s.id));
  const [focus, setFocus] = useState<AttributeName | null>(null);
  const highest = (sheet: CharacterSheet) =>
    FOCUS.reduce((best, f) => (sheetBase(sheet, f.value) > sheetBase(sheet, best) ? f.value : best), "BODY" as AttributeName);

  return (
    <PickerModal<CharacterSheet>
      title="Choose a character sheet"
      subtitle="Your starting attributes"
      searchPlaceholder="Search sheets"
      filters={<FilterChips label="Highest attribute" options={FOCUS} value={focus} onChange={setFocus} />}
      filterKey={`${focus ?? ""}|${sheets.length}`}
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
          <span className="flex items-center gap-2 font-semibold text-slate-900">
            {s.name}
            {campaignIds.has(s.id) && <span className="rounded bg-violet-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-violet-700">Campaign</span>}
          </span>
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
