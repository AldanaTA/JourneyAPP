import { lazy, Suspense, useState } from "react";
import { Check, ChevronRight, CircleAlert, CircleCheck, Lock } from "lucide-react";
import { finalizeCharacter } from "@/apis/characterApi";
import { cn } from "@/lib/utils";
import type { Race } from "@/models/Race";
import type { Trait } from "@/models/Trait";
import type { Draft, DraftRace } from "../draft";
import { draftTrait } from "../draft";
import { useDraftStore } from "../draftStore";
import { useBudget, useDraft, useReadOnly, useSheet, useUpdate } from "../hooks";
import { MIXED_RACE_TRAIT_EXP, SIZE_NAMES, allowedSizes, creationProblems } from "../rules";
import { TraitInfoButton } from "../TraitInfoButton";
import { Panel, PrimaryButton, TextField } from "../ui";

const SheetPicker = lazy(() => import("../pickers/SheetPicker"));
const RacePicker = lazy(() => import("../pickers/RacePicker"));

const TEXT_FIELDS: { key: keyof Draft & string; label: string; multiline?: boolean }[] = [
  { key: "description", label: "Description", multiline: true },
  { key: "motive", label: "Motive", multiline: true },
  { key: "ideals", label: "Ideals", multiline: true },
  { key: "strengths", label: "Strengths", multiline: true },
  { key: "flaws", label: "Flaws", multiline: true },
  { key: "history", label: "History", multiline: true },
  { key: "importantCharacters", label: "Important characters", multiline: true },
  { key: "learnedLanguages", label: "Learned languages" },
  { key: "notes", label: "Notes", multiline: true },
];

/** Applies a new set of races: keeps what still fits, and gives a single race all of its racial traits. */
function applyRaces(d: Draft, picked: Race[]) {
  const wasMixed = d.races.length > 1;
  const old = new Map(d.races.map((r) => [r.id, r]));
  d.races = picked.map(
    (r): DraftRace =>
      old.get(r.id) ?? {
        id: r.id,
        name: r.raceName,
        minSize: r.minRaceSize,
        maxSize: r.maxRaceSize,
        movement: r.movement,
        languages: r.languages,
        traits: r.traits,
      },
  );
  const ids = new Set(d.races.map((r) => r.id));
  const mixed = d.races.length > 1;
  // Racial traits of removed races go; going from one race to a mixed race starts the picks over.
  d.traits = d.traits.filter((t) => t.source !== "RACIAL" || (ids.has(t.raceId ?? "") && (wasMixed || !mixed)));
  if (d.races.length === 1) {
    d.languageRaceId = d.races[0].id;
    for (const trait of d.races[0].traits ?? []) {
      if (!d.traits.some((t) => t.traitId === trait.id)) d.traits.push(draftTrait(trait, "RACIAL", d.races[0].id, 0));
    }
  } else if (!ids.has(d.languageRaceId ?? "")) {
    d.languageRaceId = null;
  }
  if (d.size && !allowedSizes(d).includes(d.size)) d.size = null;
  if (!d.size && allowedSizes(d).length === 1) d.size = allowedSizes(d)[0];
}

function SelectButton({ label, value, onClick, disabled }: { label: string; value: string | null; onClick: () => void; disabled: boolean }) {
  return (
    <div className="flex items-center justify-between gap-3 py-2">
      <span className="text-sm font-medium text-slate-600">{label}</span>
      <button
        type="button"
        onClick={onClick}
        disabled={disabled}
        className={cn(
          "flex min-w-0 max-w-[65%] items-center gap-1 rounded-lg border px-3 py-1.5 text-sm font-semibold transition",
          value ? "border-slate-200 bg-white text-slate-900 hover:border-violet-300" : "border-violet-300 bg-violet-50 text-violet-700 hover:bg-violet-100",
          disabled && "pointer-events-none",
        )}
      >
        <span className="truncate">{value ?? "Select"}</span>
        {!disabled && <ChevronRight size={15} className="shrink-0" />}
      </button>
    </div>
  );
}

export default function IdentityTab() {
  const d = useDraft();
  const readOnly = useReadOnly();
  const update = useUpdate();
  const sheet = useSheet();
  const b = useBudget();
  const [picker, setPicker] = useState<"sheet" | "race" | null>(null);

  const mixed = d.races.length > 1;
  const sizes = allowedSizes(d);
  const problems = creationProblems(d, b);

  return (
    <div className="space-y-4">
      <Panel title="Identity">
        <div className="space-y-3">
          <TextField label="Name" value={d.name} disabled={readOnly} onChange={(v) => update((x) => void (x.name = v))} />
          {TEXT_FIELDS.map((field) => (
            <TextField
              key={field.key}
              label={field.label}
              multiline={field.multiline}
              rows={2}
              disabled={readOnly}
              value={String(d[field.key] ?? "")}
              onChange={(v) => update((x) => void ((x as unknown as Record<string, string>)[field.key] = v))}
            />
          ))}
        </div>
      </Panel>

      <Panel title="Origin">
        <div className="divide-y divide-slate-100">
          <SelectButton label="Character sheet" value={sheet?.name ?? null} disabled={readOnly} onClick={() => setPicker("sheet")} />
          <SelectButton
            label={mixed ? "Races (mixed)" : "Race"}
            value={d.races.length ? d.races.map((r) => r.name).join(" / ") : null}
            disabled={readOnly}
            onClick={() => setPicker("race")}
          />
          {sizes.length > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-2 py-2">
              <span className="text-sm font-medium text-slate-600">Size</span>
              <div className="flex flex-wrap gap-1.5">
                {sizes.map((size) => (
                  <button
                    key={size}
                    type="button"
                    disabled={readOnly}
                    aria-pressed={d.size === size}
                    onClick={() => update((x) => void (x.size = size))}
                    className={cn(
                      "rounded-full border px-2.5 py-1 text-xs font-semibold",
                      d.size === size ? "border-violet-500 bg-violet-600 text-white" : "border-slate-200 bg-white text-slate-600",
                    )}
                  >
                    {SIZE_NAMES[size]}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        {d.races.length > 0 && (
          <div className="mt-3 space-y-4">
            <div>
              <h4 className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500">Language set</h4>
              <div className="space-y-1.5">
                {d.races.map((race) => {
                  const chosen = d.languageRaceId === race.id;
                  return (
                    <button
                      key={race.id}
                      type="button"
                      disabled={readOnly || !mixed}
                      onClick={() => update((x) => void (x.languageRaceId = race.id))}
                      className={cn(
                        "flex w-full items-center gap-3 rounded-xl border px-3 py-2 text-left text-sm",
                        chosen ? "border-violet-400 bg-violet-50" : "border-slate-200 bg-white",
                      )}
                    >
                      <span
                        className={cn(
                          "flex h-4 w-4 shrink-0 items-center justify-center rounded-full border",
                          chosen ? "border-violet-600 bg-violet-600" : "border-slate-300",
                        )}
                      >
                        {chosen && <span className="h-1.5 w-1.5 rounded-full bg-white" />}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block font-medium text-slate-900">{race.languages || "No languages listed"}</span>
                        <span className="block text-xs text-slate-500">{race.name}</span>
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
            <RacialTraits />
          </div>
        )}
      </Panel>

      {d.status === "DRAFT" && <CreationChecklist problems={problems} />}

      <Suspense fallback={null}>
        {picker === "sheet" && (
          <SheetPicker currentId={d.sheetId} onPick={(s) => update((x) => void (x.sheetId = s.id))} onClose={() => setPicker(null)} />
        )}
        {picker === "race" && (
          <RacePicker
            current={d.races.map((r) => ({
              id: r.id,
              raceName: r.name,
              minRaceSize: r.minSize,
              maxRaceSize: r.maxSize,
              movement: r.movement ?? 0,
              languages: r.languages,
              raceDescription: null,
              traits: r.traits ?? [],
              contentSourceName: null,
            }))}
            onPick={(races) => update((x) => applyRaces(x, races))}
            onClose={() => setPicker(null)}
          />
        )}
      </Suspense>
    </div>
  );
}

/**
 * The racial traits box. A single race has all of its racial traits; a mixed race picks from all of its races'
 * traits, worth 50 EXP together.
 */
function RacialTraits() {
  const d = useDraft();
  const readOnly = useReadOnly();
  const update = useUpdate();
  const b = useBudget();
  const mixed = d.races.length > 1;
  const options: { trait: Trait; race: DraftRace }[] = d.races.flatMap((race) => (race.traits ?? []).map((trait) => ({ trait, race })));
  const loading = d.races.some((r) => r.traits === null);

  const toggle = (trait: Trait, race: DraftRace, on: boolean) =>
    update((x) => {
      if (on) x.traits = x.traits.filter((t) => !(t.traitId === trait.id && t.source === "RACIAL"));
      else x.traits.push(draftTrait(trait, "RACIAL", race.id, 0));
    });

  return (
    <div>
      <div className="mb-1.5 flex items-baseline justify-between">
        <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Racial traits</h4>
        {mixed && (
          <span className={cn("text-xs font-semibold tabular-nums", b.racialTraitCost === MIXED_RACE_TRAIT_EXP ? "text-emerald-700" : "text-amber-700")}>
            {b.racialTraitCost} / {MIXED_RACE_TRAIT_EXP} EXP
          </span>
        )}
      </div>
      {loading && <p className="text-sm text-slate-500">Loading racial traits…</p>}
      <ul className="space-y-1.5">
        {options.map(({ trait, race }) => {
          const owned = d.traits.find((t) => t.traitId === trait.id);
          const on = owned?.source === "RACIAL";
          const ownedOtherwise = owned !== undefined && !on;
          const cost = trait.purchaseCost ?? 0;
          const tooMuch = mixed && !on && b.racialTraitCost + cost > MIXED_RACE_TRAIT_EXP;
          const locked = readOnly || !mixed || ownedOtherwise || tooMuch;
          return (
            <li key={`${race.id}:${trait.id}`}>
              <div className="flex items-center gap-1">
<button
                type="button"
                disabled={locked}
                onClick={() => toggle(trait, race, on)}
                className={cn(
                  "flex min-w-0 flex-1 w-full items-center gap-3 rounded-xl border px-3 py-2 text-left",
                  on ? "border-violet-300 bg-violet-50" : "border-slate-200 bg-white",
                  (tooMuch || ownedOtherwise) && "opacity-55",
                )}
              >
                <span
                  className={cn(
                    "flex h-5 w-5 shrink-0 items-center justify-center rounded-md border",
                    on ? "border-violet-600 bg-violet-600 text-white" : "border-slate-300 bg-white",
                  )}
                >
                  {on && (mixed ? <Check size={13} strokeWidth={3} /> : <Lock size={11} />)}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-medium text-slate-900">{trait.traitName}</span>
                  <span className="block text-xs text-slate-500">
                    {mixed ? `${race.name} · ` : ""}
                    {cost} EXP{ownedOtherwise ? " · already has it" : tooMuch ? " · over 50 EXP" : ""}
                  </span>
                </span>
              </button>
<TraitInfoButton trait={trait} />
</div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/** What is still missing before the draft can become an active character, and the button to finish it. */
function CreationChecklist({ problems }: { problems: string[] }) {
  const readOnly = useReadOnly();
  const save = useDraftStore((state) => state.save);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const finish = async () => {
    setBusy(true);
    setError(null);
    try {
      if (!(await save())) throw new Error(useDraftStore.getState().saveError ?? "Could not save");
      const id = useDraftStore.getState().draft?.id;
      if (!id) throw new Error("Save the character first");
      const finished = await finalizeCharacter(id);
      useDraftStore.getState().patchQuietly((x) => void (x.status = finished.status));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not finish the character");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Panel title="Character creation">
      {problems.length === 0 ? (
        <p className="flex items-center gap-2 text-sm font-medium text-emerald-700">
          <CircleCheck size={16} /> Every creation rule is met.
        </p>
      ) : (
        <ul className="space-y-1">
          {problems.map((problem) => (
            <li key={problem} className="flex items-start gap-2 text-sm text-slate-700">
              <CircleAlert size={15} className="mt-0.5 shrink-0 text-amber-600" /> {problem}
            </li>
          ))}
        </ul>
      )}
      {error && <p className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      {!readOnly && (
        <div className="mt-3 flex justify-end">
          <PrimaryButton disabled={problems.length > 0 || busy} onClick={() => void finish()}>
            Finish creation
          </PrimaryButton>
        </div>
      )}
    </Panel>
  );
}
