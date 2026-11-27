import { create } from "zustand";
import { listCampaignSheets } from "@/apis/campaignApi";
import { getCharacterReferenceData, getContentSources } from "@/apis/characterApi";
import type { CharacterSheet, ContentSource, SkillInfo } from "@/models/Character";
import { useDraftStore } from "./draftStore";

/** What every character editor needs: the sheets, the skills, and the content sources the user can use. */
interface ReferenceState {
  sheets: CharacterSheet[];
  skills: SkillInfo[];
  sources: ContentSource[];
  status: "idle" | "loading" | "ready" | "error";
  /** Campaigns' own sheets, by campaign id (loaded when a campaign character is open). */
  campaignSheets: Record<string, CharacterSheet[]>;
  loadCampaignSheets: (campaignId: string) => Promise<void>;
  error: string | null;
  load: () => Promise<void>;
}

export const useReferenceStore = create<ReferenceState>((set, get) => ({
  sheets: [],
  skills: [],
  sources: [],
  status: "idle",
  campaignSheets: {},
  loadCampaignSheets: async (campaignId) => {
    if (get().campaignSheets[campaignId]) return;
    try {
      const list = await listCampaignSheets(campaignId);
      set({ campaignSheets: { ...get().campaignSheets, [campaignId]: list } });
    } catch {
      // Without them the official sheets are still offered.
    }
  },
  error: null,
  load: async () => {
    if (get().status === "loading" || get().status === "ready") return;
    set({ status: "loading", error: null });
    try {
      const [reference, sources] = await Promise.all([getCharacterReferenceData(), getContentSources()]);
      set({ sheets: reference.sheets, skills: reference.skills, sources, status: "ready" });
    } catch (error) {
      set({ status: "error", error: error instanceof Error ? error.message : "Could not load the character data" });
    }
  },
}));

/** The sources pickers search: the core rules plus the character's chosen sources. */
export function useSearchSourceIds(): string[] {
  const sources = useReferenceStore((state) => state.sources);
  const chosen = useDraftStore((state) => state.draft?.sourceIds);
  const core = sources.filter((s) => s.kind === "core").map((s) => s.id);
  return [...core, ...(chosen ?? [])];
}
