import { lazy, Suspense, useEffect, useState } from "react";
import type { LucideIcon } from "lucide-react";
import { Maximize2, MessagesSquare, Minus, NotebookPen, Plus, Search, Users, X } from "lucide-react";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import { useCampaign } from "../campaignStore";
import { useChatStore } from "./chatStore";

const SearchTool = lazy(() => import("./SearchTool"));
const PlayersTool = lazy(() => import("./PlayersTool"));
const ChatTool = lazy(() => import("./ChatTool"));
const NotesBrowser = lazy(() => import("../notes/NotesBrowser"));

type Tool = "search" | "players" | "chat" | "notes";
interface PlayWindow {
  id: string;
  tool: Tool;
  /** Collapsed to its title bar (its tool is not kept open). */
  minimized?: boolean;
}

const TOOLS: { id: Tool; label: string; icon: LucideIcon }[] = [
  { id: "search", label: "Search", icon: Search },
  { id: "players", label: "Players", icon: Users },
  { id: "chat", label: "Chat", icon: MessagesSquare },
  { id: "notes", label: "Notes", icon: NotebookPen },
];

const DEFAULT_WINDOWS: PlayWindow[] = [
  { id: "w1", tool: "chat" },
  { id: "w2", tool: "players" },
  { id: "w3", tool: "search" },
];

function storageKey(campaignId: string) {
  return `journey:play-windows:${campaignId}`;
}

function loadWindows(campaignId: string): PlayWindow[] {
  try {
    const saved = JSON.parse(window.localStorage.getItem(storageKey(campaignId)) ?? "null") as PlayWindow[] | null;
    if (Array.isArray(saved) && saved.every((w) => TOOLS.some((t) => t.id === w.tool))) return saved;
  } catch {
    // Fall back to the default layout.
  }
  return DEFAULT_WINDOWS;
}

/**
 * Play mode: as many windows as the storyteller wants, side by side on wide screens and stacked on phones. Each
 * window shows one tool (search, players, chat, notes), switched with its four icons, and can be minimized to its
 * title bar or closed. The layout is remembered per campaign on this device.
 */
export default function PlayView() {
  const campaign = useCampaign();
  const [windows, setWindows] = useState<PlayWindow[]>(() => loadWindows(campaign.id));
  const startChat = useChatStore((state) => state.start);
  const unread = useChatStore((state) => state.unread);

  useEffect(() => startChat(campaign.id), [campaign.id, startChat]);
  useEffect(() => {
    window.localStorage.setItem(storageKey(campaign.id), JSON.stringify(windows));
  }, [campaign.id, windows]);

  const add = () => setWindows([...windows, { id: `w${Date.now()}`, tool: "search" }]);
  // Picking a tool also opens a minimized window.
  const setTool = (id: string, tool: Tool) => setWindows(windows.map((w) => (w.id === id ? { ...w, tool, minimized: false } : w)));
  const toggleMinimized = (id: string) => setWindows(windows.map((w) => (w.id === id ? { ...w, minimized: !w.minimized } : w)));
  const close = (id: string) => setWindows(windows.filter((w) => w.id !== id));

  return (
    <main className="flex-1 overflow-y-auto overscroll-contain p-3">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-xs font-medium text-slate-500">{windows.length} window{windows.length === 1 ? "" : "s"}</span>
        <button type="button" onClick={add} className="flex items-center gap-1.5 rounded-lg bg-violet-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-violet-700">
          <Plus size={15} /> Window
        </button>
      </div>
      {windows.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-slate-300 bg-white/60 px-4 py-10 text-center text-sm text-slate-500">
          No windows open. Add one for search, players, chat, or notes.
        </p>
      ) : (
        <div className="grid gap-3 [grid-template-columns:repeat(auto-fill,minmax(min(100%,360px),1fr))]">
          {windows.map((w) => (
            <section
              key={w.id}
              className={cn(
                "flex flex-col overflow-hidden rounded-2xl border border-slate-200 bg-[#fffdfb] shadow-sm",
                w.minimized ? "self-start" : "h-[min(78dvh,720px)]",
              )}
            >
              <header className="flex items-center gap-1 border-b border-slate-200 bg-white px-2 py-1.5">
                {TOOLS.map(({ id, label, icon: Icon }) => (
                  <button
                    key={id}
                    type="button"
                    onClick={() => setTool(w.id, id)}
                    aria-pressed={w.tool === id}
                    title={label}
                    className={cn(
                      "relative flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-semibold transition",
                      w.tool === id ? "bg-violet-100 text-violet-800" : "text-slate-500 hover:bg-slate-100 hover:text-slate-800",
                    )}
                  >
                    <Icon size={15} />
                    <span className="hidden sm:inline">{label}</span>
                    {id === "chat" && unread > 0 && (w.tool !== "chat" || w.minimized) && (
                      <span className="absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full bg-red-500" aria-label={`${unread} unread`} />
                    )}
                  </button>
                ))}
                {w.minimized && (
                  <span className="ml-1 truncate text-xs font-medium text-slate-500">{TOOLS.find((t) => t.id === w.tool)?.label}</span>
                )}
                <button
                  type="button"
                  onClick={() => toggleMinimized(w.id)}
                  aria-label={w.minimized ? "Restore window" : "Minimize window"}
                  title={w.minimized ? "Restore" : "Minimize"}
                  className="ml-auto rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                >
                  {w.minimized ? <Maximize2 size={15} /> : <Minus size={16} />}
                </button>
                <button type="button" onClick={() => close(w.id)} aria-label="Close window" className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700">
                  <X size={16} />
                </button>
              </header>
              {!w.minimized && <div className={cn("min-h-0 flex-1", w.tool === "chat" ? "flex flex-col" : "overflow-y-auto p-3")}>
                <Suspense fallback={<div className="flex justify-center py-8 text-slate-400"><Spinner className="h-5 w-5" /></div>}>
                  {w.tool === "search" && <SearchTool />}
                  {w.tool === "players" && <PlayersTool />}
                  {w.tool === "chat" && <ChatTool />}
                  {w.tool === "notes" && <NotesBrowser compact />}
                </Suspense>
              </div>}
            </section>
          ))}
        </div>
      )}
    </main>
  );
}
