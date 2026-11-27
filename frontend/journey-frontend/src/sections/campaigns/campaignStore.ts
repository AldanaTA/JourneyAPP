import { create } from "zustand";
import type { Campaign, CampaignMember } from "@/apis/campaignApi";
import { getCampaign, listMembers } from "@/apis/campaignApi";

/** The open campaign and its members, shared by every prep tab and play window. */
interface CampaignState {
  campaign: Campaign | null;
  members: CampaignMember[];
  error: string | null;
  load: (id: string) => Promise<void>;
  setCampaign: (campaign: Campaign) => void;
  setMembers: (members: CampaignMember[]) => void;
  reloadMembers: () => Promise<void>;
  clear: () => void;
}

export const useCampaignStore = create<CampaignState>((set, get) => ({
  campaign: null,
  members: [],
  error: null,
  load: async (id) => {
    set({ error: null });
    try {
      const [campaign, members] = await Promise.all([getCampaign(id), listMembers(id)]);
      set({ campaign, members });
    } catch (error) {
      set({ error: error instanceof Error ? error.message : "Could not load the campaign" });
    }
  },
  setCampaign: (campaign) => set({ campaign }),
  setMembers: (members) => set({ members }),
  reloadMembers: async () => {
    const id = get().campaign?.id;
    if (id) set({ members: await listMembers(id) });
  },
  clear: () => set({ campaign: null, members: [], error: null }),
}));

/** The open campaign (components below CampaignPage render only once it is loaded). */
export function useCampaign(): Campaign {
  const campaign = useCampaignStore((state) => state.campaign);
  if (!campaign) throw new Error("No campaign is open");
  return campaign;
}

export function isStoryteller(campaign: Campaign): boolean {
  return campaign.role !== "PLAYER";
}
