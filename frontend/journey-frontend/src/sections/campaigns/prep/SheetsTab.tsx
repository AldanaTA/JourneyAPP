import { useEffect, useState } from "react";
import { Trash2 } from "lucide-react";
import { createSheet, deleteSheet, listCampaignSheets, updateSheet } from "@/apis/campaignApi";
import type { CharacterSheet } from "@/models/Character";
import { ConfirmDialog, EmptyNote, Fab, Modal, PrimaryButton, SecondaryButton } from "../../characters/editor/ui";
import { useCampaign } from "../campaignStore";
import { Area, Num, Row, Text } from "../forms/FormKit";

/** The campaign's own character sheets (starting attributes), offered next to the official ones. */
export default function SheetsTab() {
  const campaign = useCampaign();
  const [sheets, setSheets] = useState<CharacterSheet[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<CharacterSheet | "new" | null>(null);

  useEffect(() => {
    listCampaignSheets(campaign.id)
      .then(setSheets)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Could not load the sheets"));
  }, [campaign.id]);

  return (
    <div className="space-y-3">
      <div>
        <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">Campaign content</p>
        <h2 className="text-2xl font-semibold tracking-tight text-slate-900">Character sheets</h2>
        <p className="text-sm text-slate-600">Starting attributes players can pick besides Physical, Magic, and Balanced.</p>
      </div>
      {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      {sheets?.length === 0 && <EmptyNote>No campaign sheets yet. Tap + to make one.</EmptyNote>}
      <ul className="space-y-1.5">
        {sheets?.map((s) => (
          <li key={s.id}>
            <button
              type="button"
              onClick={() => setEditing(s)}
              className="block w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-left shadow-sm hover:border-violet-300"
            >
              <span className="block font-semibold text-slate-900">{s.name}</span>
              <span className="block text-xs tabular-nums text-slate-500">
                Body {s.baseBody} · Strength {s.baseStrength} · Speed {s.baseSpeed} · Magic {s.baseMagic}
              </span>
            </button>
          </li>
        ))}
      </ul>
      <Fab label="New sheet" onClick={() => setEditing("new")} className="bottom-24" />
      {editing && (
        <SheetModal
          campaignId={campaign.id}
          sheet={editing === "new" ? null : editing}
          onSaved={(list) => {
            setSheets(list);
            setEditing(null);
          }}
          onClose={() => setEditing(null)}
        />
      )}
    </div>
  );
}

function SheetModal({ campaignId, sheet, onSaved, onClose }: {
  campaignId: string;
  sheet: CharacterSheet | null;
  onSaved: (sheets: CharacterSheet[]) => void;
  onClose: () => void;
}) {
  const [name, setName] = useState(sheet?.name ?? "");
  const [description, setDescription] = useState(sheet?.description ?? "");
  const [body, setBody] = useState<number | null>(sheet?.baseBody ?? 30);
  const [strength, setStrength] = useState<number | null>(sheet?.baseStrength ?? 25);
  const [speed, setSpeed] = useState<number | null>(sheet?.baseSpeed ?? 25);
  const [magic, setMagic] = useState<number | null>(sheet?.baseMagic ?? 20);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState(false);

  const run = async (work: () => Promise<CharacterSheet[]>) => {
    setBusy(true);
    setError(null);
    try {
      onSaved(await work());
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not save");
      setBusy(false);
    }
  };
  const values = { name: name.trim(), description, base_body: body ?? 0, base_strength: strength ?? 0, base_speed: speed ?? 0, base_magic: magic ?? 0 };

  return (
    <Modal
      title={sheet ? `Edit ${sheet.name}` : "New character sheet"}
      onClose={onClose}
      footer={
        <div className="flex w-full items-center justify-between">
          {sheet ? (
            <button type="button" onClick={() => setConfirm(true)} className="flex items-center gap-1.5 rounded-lg px-2 py-2 text-sm font-semibold text-red-700 hover:bg-red-50">
              <Trash2 size={15} /> Delete
            </button>
          ) : (
            <span />
          )}
          <span className="flex gap-2">
            <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
            <PrimaryButton
              disabled={busy || !name.trim()}
              onClick={() => void run(() => (sheet ? updateSheet(campaignId, sheet.id, values) : createSheet(campaignId, values)))}
            >
              {sheet ? "Save" : "Create"}
            </PrimaryButton>
          </span>
        </div>
      }
    >
      <div className="space-y-3">
        <Text label="Name" value={name} onChange={setName} />
        <Area label="Description" value={description} onChange={setDescription} rows={2} />
        <Row>
          <Num label="Body" value={body} onChange={setBody} />
          <Num label="Strength" value={strength} onChange={setStrength} />
          <Num label="Speed" value={speed} onChange={setSpeed} />
          <Num label="Magic" value={magic} onChange={setMagic} />
        </Row>
        <p className="text-xs text-slate-500">Total {(body ?? 0) + (strength ?? 0) + (speed ?? 0) + (magic ?? 0)} (the official sheets total 100).</p>
      </div>
      {error && <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      {confirm && sheet && (
        <ConfirmDialog
          title={`Delete ${sheet.name}?`}
          message="Characters using it must be given another sheet first."
          confirmLabel="Delete"
          onClose={() => setConfirm(false)}
          onConfirm={() => {
            setConfirm(false);
            void run(() => deleteSheet(campaignId, sheet.id));
          }}
        />
      )}
    </Modal>
  );
}
