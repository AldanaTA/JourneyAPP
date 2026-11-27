import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, MessagesSquare, Search } from "lucide-react";
import { createPortal } from "react-dom";
import { useParams } from "react-router-dom";
import { sendMessage, sendRoll } from "@/apis/campaignApi";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import CharacterEditor from "../characters/editor/CharacterEditor";
import { useDraftStore } from "../characters/editor/draftStore";
import type { PlayActions } from "../characters/editor/playContext";
import { useCampaignStore } from "./campaignStore";
import { useChatStore } from "./play/chatStore";

const ChatTool = lazy(() => import("./play/ChatTool"));
const SearchTool = lazy(() => import("./play/SearchTool"));

const DEGREE_NAMES: Record<string, string> = {
  CRITICAL: "Critical success", SUPERIOR: "Superior success", MAJOR: "Major success", SUCCESS: "Success", FAILURE: "Failure", FUMBLE: "Fumble",
};

/**
 * A player playing their campaign version: the usual character sheet, where skills roll and powers and traits
 * spend when tapped, with a notch on the left that slides out the campaign chat and the compendium search.
 * Route: /campaigns/:campaignId/characters/:characterId.
 */
export default function CampaignCharacterPlay() {
  const { campaignId = "" } = useParams();
  const campaign = useCampaignStore((state) => state.campaign);
  const loadCampaign = useCampaignStore((state) => state.load);
  const startChat = useChatStore((state) => state.start);
  const unread = useChatStore((state) => state.unread);
  const [toast, setToast] = useState<{ text: string; tone: "ok" | "warn" } | null>(null);
  const toastTimer = useRef<number | undefined>(undefined);

  useEffect(() => {
    void loadCampaign(campaignId);
  }, [campaignId, loadCampaign]);
  useEffect(() => startChat(campaignId), [campaignId, startChat]);

  const notify = useCallback((text: string, tone: "ok" | "warn" = "ok") => {
    setToast({ text, tone });
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 3500);
  }, []);

  const play = useMemo<PlayActions>(() => {
    const name = () => useDraftStore.getState().draft?.name?.trim() || "Someone";
    return {
      campaignId,
      notify,
      announce: (text) => {
        sendMessage(campaignId, { body: `${name()} ${text}` }).catch(() => notify("Could not post to the chat", "warn"));
      },
      rollSkill: (label, rating) => {
        sendRoll(campaignId, { label: `${name()}: ${label}`, skill_rating: rating, modifier: 0 })
          .then((message) => {
            const data = (message.data ?? {}) as { roll?: number; degree?: string };
            notify(`${label}: ${data.roll ?? "?"} vs ${rating}% — ${DEGREE_NAMES[data.degree ?? ""] ?? data.degree ?? ""}`,
              data.degree === "FAILURE" || data.degree === "FUMBLE" ? "warn" : "ok");
          })
          .catch((reason: unknown) => notify(reason instanceof Error ? reason.message : "Could not roll", "warn"));
      },
    };
  }, [campaignId, notify]);

  return (
    <>
      <CharacterEditor mode="edit" play={play} drawer={campaign?.id === campaignId ? <SidePanel unread={unread} /> : null} />
      {toast &&
        createPortal(
          <div className="pointer-events-none fixed inset-x-0 bottom-24 z-[65] flex justify-center px-4" role="status" aria-live="polite">
            <div className={cn("rounded-xl px-4 py-2.5 text-sm font-semibold shadow-lg", toast.tone === "warn" ? "bg-amber-500 text-amber-950" : "bg-slate-900 text-white")}>
              {toast.text}
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}

/** The notch on the left edge, and the panel it slides out: the campaign chat and the compendium search. */
function SidePanel({ unread }: { unread: number }) {
  const [open, setOpen] = useState(false);
  const [tool, setTool] = useState<"chat" | "search">("chat");
  return (
    <>
      {!open && (
        <button
          type="button"
          onClick={() => setOpen(true)}
          aria-label="Open chat and search"
          className="absolute left-0 top-1/2 z-30 flex -translate-y-1/2 flex-col items-center gap-1 rounded-r-xl bg-[#201a12] px-1.5 py-4 text-white shadow-lg"
        >
          <MessagesSquare size={16} />
          <ChevronRight size={14} />
          {unread > 0 && <span className="absolute -right-1 -top-1 h-3 w-3 rounded-full bg-red-500 ring-2 ring-[#f5efe7]" aria-label={`${unread} unread`} />}
        </button>
      )}
      {open && (
        <>
          <div className="absolute inset-0 z-30 bg-slate-950/30" onClick={() => setOpen(false)} aria-hidden="true" />
          <aside className="absolute inset-y-0 left-0 z-30 flex w-[min(92vw,420px)] flex-col bg-[#fffdfb] shadow-2xl">
            <header className="flex items-center gap-1 border-b border-slate-200 px-2 py-1.5">
              {([["chat", "Chat", MessagesSquare], ["search", "Search", Search]] as const).map(([value, label, Icon]) => (
                <button key={value} type="button" onClick={() => setTool(value)} aria-pressed={tool === value}
                  className={cn("flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold", tool === value ? "bg-violet-100 text-violet-800" : "text-slate-500 hover:bg-slate-100")}>
                  <Icon size={15} /> {label}
                </button>
              ))}
              <button type="button" onClick={() => setOpen(false)} aria-label="Close panel" className="ml-auto rounded-lg p-1.5 text-slate-500 hover:bg-slate-100">
                <ChevronLeft size={18} />
              </button>
            </header>
            <div className={cn("min-h-0 flex-1", tool === "chat" ? "flex flex-col" : "overflow-y-auto p-3")}>
              <Suspense fallback={<div className="flex justify-center py-8 text-slate-400"><Spinner className="h-5 w-5" /></div>}>
                {tool === "chat" ? <ChatTool /> : <SearchTool />}
              </Suspense>
            </div>
          </aside>
        </>
      )}
    </>
  );
}
