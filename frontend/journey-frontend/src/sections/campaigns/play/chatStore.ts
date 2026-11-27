import { create } from "zustand";
import type { ChatMessage } from "@/apis/campaignApi";
import { followChat, getMessages, markChatRead } from "@/apis/campaignApi";
import { useAuthStore } from "@/auth/authStore";

/**
 * The open campaign's chat, shared by every chat window: one live stream, messages oldest first. {@code whisperTo}
 * lets the players window start a whisper in the chat windows.
 */
interface ChatState {
  campaignId: string | null;
  messages: ChatMessage[];
  nextBefore: string | null;
  unread: number;
  loading: boolean;
  error: string | null;
  whisperTo: { ids: string[]; seq: number };
  start: (campaignId: string) => () => void;
  loadOlder: () => Promise<void>;
  markRead: () => void;
  upsert: (message: ChatMessage) => void;
  startWhisper: (userIds: string[]) => void;
}

let stopStream: (() => void) | null = null;

export const useChatStore = create<ChatState>((set, get) => ({
  campaignId: null,
  messages: [],
  nextBefore: null,
  unread: 0,
  loading: false,
  error: null,
  whisperTo: { ids: [], seq: 0 },

  start: (campaignId) => {
    stopStream?.();
    set({ campaignId, messages: [], nextBefore: null, unread: 0, loading: true, error: null });
    const loadLatest = () =>
      getMessages(campaignId)
        .then((page) => {
          if (get().campaignId !== campaignId) return;
          // Merge with anything that arrived live meanwhile.
          const live = get().messages.filter((m) => !page.messages.some((p) => p.id === m.id));
          set({
            messages: [...[...page.messages].reverse(), ...live],
            nextBefore: page.nextBefore,
            unread: page.unreadCount,
            loading: false,
          });
        })
        .catch((error: unknown) => set({ loading: false, error: error instanceof Error ? error.message : "Could not load the chat" }));
    void loadLatest();
    stopStream = followChat(
      campaignId,
      (type, data) => {
        if (get().campaignId !== campaignId) return;
        if (type === "message" || type === "edited") {
          const message = data as ChatMessage;
          get().upsert(message);
          if (type === "message" && message.senderId !== useAuthStore.getState().user?.id) set({ unread: get().unread + 1 });
        } else if (type === "deleted") {
          const id = (data as { id: string }).id;
          set({ messages: get().messages.filter((m) => m.id !== id) });
        }
      },
      // After a reconnect, reload what may have been missed.
      () => void loadLatest(),
    );
    return () => {
      stopStream?.();
      stopStream = null;
      set({ campaignId: null, messages: [] });
    };
  },

  loadOlder: async () => {
    const { campaignId, nextBefore } = get();
    if (!campaignId || !nextBefore) return;
    const page = await getMessages(campaignId, nextBefore);
    set({ messages: [...[...page.messages].reverse(), ...get().messages], nextBefore: page.nextBefore });
  },

  markRead: () => {
    const { campaignId, unread } = get();
    if (!campaignId || unread === 0) return;
    set({ unread: 0 });
    markChatRead(campaignId).catch(() => undefined);
  },

  upsert: (message) => {
    const messages = get().messages;
    const index = messages.findIndex((m) => m.id === message.id);
    if (index >= 0) set({ messages: messages.map((m, i) => (i === index ? message : m)) });
    else set({ messages: [...messages, message] });
  },

  startWhisper: (userIds) => set({ whisperTo: { ids: userIds, seq: get().whisperTo.seq + 1 } }),
}));
