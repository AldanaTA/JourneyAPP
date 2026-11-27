import { useEffect, useRef, useState } from "react";
import { Dices, EyeOff, MessageSquare, Send, Swords, Trash2 } from "lucide-react";
import type { ChatMessage } from "@/apis/campaignApi";
import { deleteMessage, sendCombatEvent, sendMessage, sendRoll } from "@/apis/campaignApi";
import { useAuthStore } from "@/auth/authStore";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import { inputClass } from "../../characters/editor/ui";
import { isStoryteller, useCampaign, useCampaignStore } from "../campaignStore";
import { useChatStore } from "./chatStore";

const DEGREE_STYLES: Record<string, string> = {
  CRITICAL: "bg-amber-400 text-amber-950",
  SUPERIOR: "bg-emerald-500 text-white",
  MAJOR: "bg-emerald-400 text-emerald-950",
  SUCCESS: "bg-emerald-100 text-emerald-800",
  FAILURE: "bg-slate-200 text-slate-700",
  FUMBLE: "bg-red-500 text-white",
};
const DEGREE_NAMES: Record<string, string> = {
  CRITICAL: "Critical", SUPERIOR: "Superior", MAJOR: "Major", SUCCESS: "Success", FAILURE: "Failure", FUMBLE: "Fumble",
};

function time(iso: string) {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "" : d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

/** The campaign chat: everything so far (older on request), and a box to talk, roll, or log combat. */
export default function ChatTool() {
  const messages = useChatStore((state) => state.messages);
  const loading = useChatStore((state) => state.loading);
  const error = useChatStore((state) => state.error);
  const nextBefore = useChatStore((state) => state.nextBefore);
  const loadOlder = useChatStore((state) => state.loadOlder);
  const markRead = useChatStore((state) => state.markRead);
  const listRef = useRef<HTMLDivElement>(null);
  const stickToBottom = useRef(true);

  // Follow new messages while scrolled to the bottom; mark them read while this window shows them.
  useEffect(() => {
    const list = listRef.current;
    if (list && stickToBottom.current) list.scrollTop = list.scrollHeight;
    if (document.visibilityState === "visible") markRead();
  }, [messages, markRead]);

  return (
    <>
      <div
        ref={listRef}
        onScroll={(event) => {
          const el = event.currentTarget;
          stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 40;
        }}
        className="min-h-0 flex-1 space-y-1.5 overflow-y-auto px-3 py-2"
      >
        {nextBefore && (
          <div className="flex justify-center">
            <button type="button" onClick={() => void loadOlder()} className="text-xs font-medium text-violet-700">Load earlier messages</button>
          </div>
        )}
        {loading && <div className="flex justify-center py-6 text-slate-400"><Spinner className="h-5 w-5" /></div>}
        {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
        {!loading && messages.length === 0 && <p className="py-6 text-center text-sm text-slate-400">No messages yet.</p>}
        {messages.map((m) => <MessageLine key={m.id} message={m} />)}
      </div>
      <Composer />
    </>
  );
}

function MessageLine({ message: m }: { message: ChatMessage }) {
  const campaign = useCampaign();
  const me = useAuthStore((state) => state.user?.id);
  const canDelete = m.kind !== "SYSTEM" && (m.senderId === me || isStoryteller(campaign));
  const whisperTo = m.whisper ? (m.recipients.length ? m.recipients.map((r) => r.name).join(", ") : "storytellers") : null;

  if (m.kind === "SYSTEM") {
    return <p className="py-1 text-center text-xs text-slate-400">{m.body}</p>;
  }
  const data = (m.data ?? {}) as Record<string, unknown>;
  return (
    <div className={cn("group rounded-xl px-2.5 py-1.5", m.whisper ? "bg-violet-50 ring-1 ring-violet-200" : m.senderId === me ? "bg-slate-50" : "")}>
      <div className="flex items-baseline gap-2 text-xs">
        <span className="font-semibold text-slate-800">{m.senderName ?? "Someone"}</span>
        {whisperTo && <span className="flex items-center gap-1 font-medium text-violet-700"><EyeOff size={11} /> to {whisperTo}</span>}
        <span className="text-slate-400">{time(m.createdAt)}{m.editedAt ? " · edited" : ""}</span>
        {canDelete && (
          <button
            type="button"
            aria-label="Delete message"
            onClick={() => deleteMessage(campaign.id, m.id).catch(() => undefined)}
            className="ml-auto text-slate-300 opacity-0 hover:text-red-600 group-hover:opacity-100 focus:opacity-100"
          >
            <Trash2 size={12} />
          </button>
        )}
      </div>
      {m.kind === "ROLL" ? (
        <div className="mt-1 flex items-center gap-3">
          <span className="flex h-10 min-w-10 items-center justify-center rounded-lg bg-slate-900 px-2 text-lg font-bold tabular-nums text-white">
            {String(data.total ?? "?")}
          </span>
          <span className="min-w-0 flex-1 text-sm">
            <span className="block font-medium text-slate-900">{String(data.label ?? "Roll")}</span>
            <span className="block text-xs text-slate-500">
              {data.type === "skill" ? `d100 vs ${String(data.rating)}%` : String(data.formula ?? "")}
              {Array.isArray(data.dice) && (data.dice as { results: number[] }[]).flatMap((g) => g.results).length > 1
                ? ` · ${(data.dice as { results: number[] }[]).flatMap((g) => g.results).join(", ")}`
                : ""}
            </span>
          </span>
          {typeof data.degree === "string" && (
            <span className={cn("rounded-md px-2 py-1 text-xs font-bold", DEGREE_STYLES[data.degree])}>{DEGREE_NAMES[data.degree] ?? data.degree}</span>
          )}
        </div>
      ) : m.kind === "COMBAT" ? (
        <p className="mt-0.5 flex items-start gap-1.5 text-sm text-slate-800">
          <Swords size={14} className="mt-0.5 shrink-0 text-red-600" />
          <span>
            <span className="mr-1 rounded bg-red-50 px-1 text-[11px] font-semibold uppercase text-red-700">{String(data.event ?? "combat")}</span>
            {m.body}
          </span>
        </p>
      ) : (
        <p className="whitespace-pre-wrap break-words text-sm text-slate-800">{m.body}</p>
      )}
    </div>
  );
}

type Mode = "say" | "roll" | "combat";

function Composer() {
  const campaign = useCampaign();
  const members = useCampaignStore((state) => state.members);
  const me = useAuthStore((state) => state.user?.id);
  const whisperRequest = useChatStore((state) => state.whisperTo);
  const [mode, setMode] = useState<Mode>("say");
  const [whisper, setWhisper] = useState(false);
  const [to, setTo] = useState<string[]>([]);
  const [text, setText] = useState("");
  const [label, setLabel] = useState("");
  const [rollKind, setRollKind] = useState<"skill" | "formula">("skill");
  const [rating, setRating] = useState("50");
  const [modifier, setModifier] = useState("0");
  const [formula, setFormula] = useState("1d6");
  const [event, setEvent] = useState("damage");
  const [target, setTarget] = useState("");
  const [amount, setAmount] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // The players window asked to whisper someone.
  const [seenRequest, setSeenRequest] = useState(whisperRequest.seq);
  if (whisperRequest.seq !== seenRequest) {
    setSeenRequest(whisperRequest.seq);
    setMode("say");
    setWhisper(true);
    setTo(whisperRequest.ids);
  }

  const others = members.filter((m) => m.userId !== me);
  const whisperBody = whisper ? { whisper: true, whisper_to: to } : {};
  const send = async () => {
    setBusy(true);
    setError(null);
    try {
      if (mode === "say") {
        if (!text.trim()) return;
        await sendMessage(campaign.id, { body: text.trim(), ...whisperBody });
        setText("");
      } else if (mode === "roll") {
        await sendRoll(campaign.id, rollKind === "skill"
          ? { label: label.trim() || undefined, skill_rating: Number(rating) || 0, modifier: Number(modifier) || 0, ...whisperBody }
          : { label: label.trim() || undefined, formula, ...whisperBody });
      } else {
        const n = Number(amount);
        const summary = text.trim() || [target.trim(), event, amount ? String(n) : ""].filter(Boolean).join(" ");
        if (!summary) return;
        await sendCombatEvent(campaign.id, {
          event, summary, details: { target: target.trim() || undefined, amount: amount ? n : undefined }, ...whisperBody,
        });
        setText("");
        setAmount("");
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not send");
    } finally {
      setBusy(false);
    }
  };

  const small = "rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm outline-none focus:border-violet-400";
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        void send();
      }}
      className="space-y-2 border-t border-slate-200 bg-white p-2"
    >
      <div className="flex flex-wrap items-center gap-1">
        {([["say", "Say", MessageSquare], ["roll", "Roll", Dices], ["combat", "Combat", Swords]] as const).map(([value, name, Icon]) => (
          <button key={value} type="button" onClick={() => setMode(value)} aria-pressed={mode === value}
            className={cn("flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold", mode === value ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-100")}>
            <Icon size={13} /> {name}
          </button>
        ))}
        <label className="ml-auto flex items-center gap-1.5 text-xs font-medium text-violet-700">
          <input type="checkbox" checked={whisper} onChange={(e) => setWhisper(e.target.checked)} className="h-3.5 w-3.5 accent-violet-600" />
          Whisper
        </label>
      </div>
      {whisper && (
        <div className="flex flex-wrap gap-1">
          {others.map((m) => {
            const on = to.includes(m.userId);
            return (
              <button key={m.userId} type="button" onClick={() => setTo(on ? to.filter((id) => id !== m.userId) : [...to, m.userId])}
                className={cn("rounded-full border px-2 py-0.5 text-[11px] font-medium", on ? "border-violet-500 bg-violet-600 text-white" : "border-slate-200 text-slate-600")}>
                {m.displayName}
              </button>
            );
          })}
          <span className="self-center text-[11px] text-slate-500">{to.length ? "" : "Nobody picked: storytellers only"}</span>
        </div>
      )}

      {mode === "roll" && (
        <div className="flex flex-wrap items-center gap-1.5">
          <select value={rollKind} onChange={(e) => setRollKind(e.target.value as "skill" | "formula")} aria-label="Roll kind" className={small}>
            <option value="skill">Skill (d100)</option>
            <option value="formula">Dice</option>
          </select>
          <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder={rollKind === "skill" ? "Skill, e.g. Dodge" : "For, e.g. Damage"} aria-label="Roll label" className={cn(small, "min-w-0 flex-1")} />
          {rollKind === "skill" ? (
            <>
              <input value={rating} onChange={(e) => setRating(e.target.value)} inputMode="numeric" aria-label="Skill rating" className={cn(small, "w-16")} />
              <span className="text-xs text-slate-500">%</span>
              <input value={modifier} onChange={(e) => setModifier(e.target.value)} inputMode="numeric" aria-label="Modifier" title="Modifier" className={cn(small, "w-14")} />
            </>
          ) : (
            <input value={formula} onChange={(e) => setFormula(e.target.value)} aria-label="Dice formula" placeholder="2d6+3" className={cn(small, "w-28")} />
          )}
        </div>
      )}
      {mode === "combat" && (
        <div className="flex flex-wrap items-center gap-1.5">
          <select value={event} onChange={(e) => setEvent(e.target.value)} aria-label="Combat event" className={small}>
            {["damage", "healing", "condition", "turn", "other"].map((v) => <option key={v} value={v}>{v}</option>)}
          </select>
          <input value={target} onChange={(e) => setTarget(e.target.value)} placeholder="Target" aria-label="Target" className={cn(small, "min-w-0 flex-1")} />
          <input value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount" inputMode="numeric" aria-label="Amount" className={cn(small, "w-20")} />
        </div>
      )}

      <div className="flex gap-1.5">
        {mode !== "roll" && (
          <input value={text} onChange={(e) => setText(e.target.value)} aria-label={mode === "say" ? "Message" : "Summary"}
            placeholder={mode === "say" ? (whisper ? "Whisper…" : "Message…") : "What happened (optional)"} className={cn(inputClass, "py-1.5")} />
        )}
        <button type="submit" disabled={busy} aria-label="Send"
          className={cn("flex shrink-0 items-center justify-center gap-1 rounded-lg bg-violet-600 px-3 text-sm font-semibold text-white hover:bg-violet-700 disabled:opacity-50", mode === "roll" && "flex-1 py-2")}>
          {mode === "roll" ? <><Dices size={16} /> Roll</> : <Send size={16} />}
        </button>
      </div>
      {error && <p className="text-xs text-red-700">{error}</p>}
    </form>
  );
}
