import { useEffect, useState } from "react";
import { Crown, ExternalLink, EyeOff, RefreshCw, Shield, UserRound } from "lucide-react";
import type { CampaignCharacter } from "@/apis/campaignApi";
import { listCampaignCharacters } from "@/apis/campaignApi";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "@/auth/authStore";
import { returnParam } from "../../characters/editor/returnTo";
import { useCampaign, useCampaignStore } from "../campaignStore";
import { useChatStore } from "./chatStore";

/**
 * Who is in the campaign and their campaign characters; whisper someone, or open a character sheet (its X comes
 * back to play mode, with the windows as they were).
 */
export default function PlayersTool() {
  const campaign = useCampaign();
  const navigate = useNavigate();
  const members = useCampaignStore((state) => state.members);
  const reloadMembers = useCampaignStore((state) => state.reloadMembers);
  const startWhisper = useChatStore((state) => state.startWhisper);
  const me = useAuthStore((state) => state.user?.id);
  const [characters, setCharacters] = useState<CampaignCharacter[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reload = () => {
    void reloadMembers();
    listCampaignCharacters(campaign.id)
      .then(setCharacters)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Could not load the characters"));
  };
  useEffect(() => {
    listCampaignCharacters(campaign.id)
      .then(setCharacters)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Could not load the characters"));
  }, [campaign.id]);

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-700">Party</h3>
        <button type="button" onClick={reload} aria-label="Refresh" className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700">
          <RefreshCw size={14} />
        </button>
      </div>
      {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      <ul className="space-y-2">
        {members.map((m) => {
          const theirs = (characters ?? []).filter((c) => c.playerId === m.userId);
          return (
            <li key={m.userId} className="rounded-xl border border-slate-200 bg-white p-2.5">
              <div className="flex items-center gap-2">
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-slate-600">
                  {m.role === "OWNER" ? <Crown size={15} /> : m.role === "STORYTELLER" ? <Shield size={15} /> : <UserRound size={15} />}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-slate-900">{m.displayName}{m.userId === me ? " (you)" : ""}</span>
                  <span className="block text-xs text-slate-500">{m.role === "PLAYER" ? "Player" : m.role === "OWNER" ? "Storyteller" : "Co-storyteller"}</span>
                </span>
                {m.userId !== me && (
                  <button type="button" onClick={() => startWhisper([m.userId])} title="Whisper in the chat"
                    className="flex items-center gap-1 rounded-lg border border-violet-200 px-2 py-1 text-xs font-semibold text-violet-700 hover:bg-violet-50">
                    <EyeOff size={12} /> Whisper
                  </button>
                )}
              </div>
              {theirs.length > 0 && (
                <ul className="mt-2 space-y-1 border-t border-slate-100 pt-2">
                  {theirs.map((c) => (
                    <li key={c.id}>
                      <button type="button" onClick={() => navigate(`/characters/${c.id}${returnParam(`/campaigns/${campaign.id}?mode=play`)}`)}
                        className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left hover:bg-slate-50">
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm font-medium text-slate-900">{c.name}</span>
                          <span className="block truncate text-xs text-slate-500">
                            {[c.raceNames.join(" / "), c.jobName].filter(Boolean).join(" · ") || "Not finished"}
                            {c.status === "DRAFT" ? " · draft" : ""}
                            {c.disallowedContent > 0 ? ` · ${c.disallowedContent} not allowed` : ""}
                          </span>
                        </span>
                        <ExternalLink size={13} className="shrink-0 text-slate-400" />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
