import { createContext, useContext } from "react";

/**
 * What the character sheet can do while the character is played in a campaign (absent outside play): roll a skill
 * in the campaign chat, tell the table what the character did, and show a short result on the sheet.
 */
export interface PlayActions {
  campaignId: string;
  /** Rolls a d100 against the rating in the campaign chat and shows the result. */
  rollSkill: (label: string, rating: number) => void;
  /** Posts "<character> <text>" in the campaign chat. */
  announce: (text: string) => void;
  /** A short message on the sheet ("Not enough MP"). */
  notify: (text: string, tone?: "ok" | "warn") => void;
}

export const PlayContext = createContext<PlayActions | null>(null);

/** The play actions, or null when the sheet is not being played. */
export function usePlay(): PlayActions | null {
  return useContext(PlayContext);
}
