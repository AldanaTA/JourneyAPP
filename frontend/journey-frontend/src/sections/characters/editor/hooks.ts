import { useMemo } from "react";
import type { CharacterSheet } from "@/models/Character";
import type { Draft } from "./draft";
import { useDraftStore } from "./draftStore";
import { useReferenceStore } from "./referenceStore";
import type { Budget, Stats } from "./rules";
import { budget, stats } from "./rules";

/** The open character (tabs only render once it is loaded). */
export function useDraft(): Draft {
  const draft = useDraftStore((state) => state.draft);
  if (!draft) throw new Error("No character is open");
  return draft;
}

export function useReadOnly(): boolean {
  return useDraftStore((state) => state.readOnly);
}

export function useUpdate() {
  return useDraftStore((state) => state.update);
}

const NO_SHEETS: CharacterSheet[] = [];

/** The sheets the character can use: the official ones, plus its campaign's own for a campaign character. */
export function useSheetOptions(): { official: CharacterSheet[]; campaign: CharacterSheet[] } {
  const campaignId = useDraftStore((state) => state.draft?.campaignId ?? null);
  const official = useReferenceStore((state) => state.sheets);
  const campaign = useReferenceStore((state) => (campaignId ? state.campaignSheets[campaignId] : undefined)) ?? NO_SHEETS;
  return { official, campaign };
}

export function useSheet(): CharacterSheet | undefined {
  const sheetId = useDraftStore((state) => state.draft?.sheetId);
  const { official, campaign } = useSheetOptions();
  return official.find((s) => s.id === sheetId) ?? campaign.find((s) => s.id === sheetId);
}

/** EXP and job points, worked out from the draft. */
export function useBudget(): Budget {
  const draft = useDraft();
  const skills = useReferenceStore((state) => state.skills);
  return useMemo(() => budget(draft, skills), [draft, skills]);
}

export function useStats(): Stats {
  const draft = useDraft();
  const sheet = useSheet();
  return useMemo(() => stats(draft, sheet), [draft, sheet]);
}
