import { authorizedFetch, errorMessage } from "../auth/session";
import type { CharacterSheet } from "../models/Character";
import type { RuleBook, RuleChapter } from "../models/Rules";

/** Shapes and calls for /api/v1/campaigns (camelCase responses, snake_case requests). */

export type CampaignRole = "OWNER" | "STORYTELLER" | "PLAYER";

export interface CampaignSummary {
  id: string;
  name: string;
  description: string;
  role: CampaignRole;
  ownerName: string;
  playerCount: number;
  characterCount: number;
  joinEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Campaign {
  id: string;
  name: string;
  description: string;
  role: CampaignRole;
  owner: { id: string; name: string };
  joinEnabled: boolean;
  /** Players' join code (storytellers only). */
  joinCode: string | null;
  /** Co-storytellers' join code (owner only). */
  storytellerJoinCode: string | null;
  allowedSources: { id: string; name: string; kind: string }[];
  playerCount: number;
  characterCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface JoinPreview {
  campaignId: string;
  name: string;
  description: string;
  ownerName: string;
  playerCount: number;
  joinEnabled: boolean;
  alreadyMember: boolean;
  joinsAs: "PLAYER" | "STORYTELLER";
  currentRole: CampaignRole | null;
}

export interface CampaignMember {
  userId: string;
  displayName: string;
  role: CampaignRole;
  characterCount: number;
  joinedAt: string;
}

export interface CampaignCharacter {
  id: string;
  name: string;
  status: string;
  playerId: string;
  playerName: string;
  originCharacterId: string | null;
  jobName: string | null;
  raceNames: string[];
  disallowedContent: number;
  updatedAt: string;
}

export interface ContentIssue {
  kind: "RACE" | "JOB" | "SHEET" | "TRAIT" | "POWER" | "ITEM" | "SOURCE";
  id: string;
  name: string;
  source: string;
  reason: "NOT_ALLOWED" | "RACE_REMOVED" | "JOB_REMOVED";
  expSpent: number;
  quantity: number;
}

export interface ImportCheck {
  characterId: string;
  characterName: string;
  canImport: boolean;
  message: string;
  removals: ContentIssue[];
  refundedExp: number;
  becomesDraft: boolean;
}

export interface NoteTree {
  folders: { id: string; parentId: string | null; name: string; sortOrder: number }[];
  notes: { id: string; folderId: string | null; title: string; sortOrder: number; updatedAt: string }[];
}

export interface CampaignNote {
  id: string;
  folderId: string | null;
  title: string;
  body: string;
  sortOrder: number;
  authorName: string | null;
  editedByName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface NoteSearchResult {
  id: string;
  folderId: string | null;
  title: string;
  snippet: string;
}

export type MessageKind = "MESSAGE" | "ROLL" | "COMBAT" | "SYSTEM";

export interface ChatMessage {
  id: string;
  kind: MessageKind;
  senderId: string | null;
  senderName: string | null;
  whisper: boolean;
  recipients: { id: string; name: string }[];
  body: string;
  data: Record<string, unknown> | null;
  createdAt: string;
  editedAt: string | null;
}

export interface ChatPage {
  messages: ChatMessage[];
  nextBefore: string | null;
  unreadCount: number;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await authorizedFetch(path, {
    ...init,
    headers: init.body ? { "Content-Type": "application/json", ...init.headers } : init.headers,
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

const json = (method: string, body?: unknown): RequestInit => ({ method, body: body === undefined ? undefined : JSON.stringify(body) });
const c = (id: string) => `/campaigns/${encodeURIComponent(id)}`;

// Campaigns
export const listCampaigns = (signal?: AbortSignal) => request<CampaignSummary[]>("/campaigns", { signal });
export const getCampaign = (id: string, signal?: AbortSignal) => request<Campaign>(c(id), { signal });
export const createCampaign = (name: string, description: string) => request<Campaign>("/campaigns", json("POST", { name, description }));
export const updateCampaign = (id: string, body: { name?: string; description?: string; join_enabled?: boolean }) =>
  request<Campaign>(c(id), json("PATCH", body));
export const deleteCampaign = (id: string) => request<void>(c(id), { method: "DELETE" });
export const resetJoinCode = (id: string, role: "PLAYER" | "STORYTELLER") =>
  request<Campaign>(`${c(id)}/join-code?role=${role}`, { method: "POST" });
export const previewJoin = (code: string) => request<JoinPreview>(`/campaigns/join/${encodeURIComponent(code.trim())}`);
export const joinCampaign = (code: string) => request<Campaign>(`/campaigns/join/${encodeURIComponent(code.trim())}`, { method: "POST" });
export const setAllowedSources = (id: string, sourceIds: string[]) =>
  request<Campaign>(`${c(id)}/sources`, json("PUT", { source_ids: sourceIds }));

// Members
export const listMembers = (id: string) => request<CampaignMember[]>(`${c(id)}/members`);
export const setMemberRole = (id: string, userId: string, role: "STORYTELLER" | "PLAYER") =>
  request<CampaignMember[]>(`${c(id)}/members/${userId}`, json("PATCH", { role }));
export const removeMember = (id: string, userId: string) => request<void>(`${c(id)}/members/${userId}`, { method: "DELETE" });
export const transferOwnership = (id: string, userId: string) => request<Campaign>(`${c(id)}/owner`, json("POST", { user_id: userId }));

// Characters
export const listCampaignCharacters = (id: string) => request<CampaignCharacter[]>(`${c(id)}/characters`);
export const checkImport = (id: string, characterId: string) =>
  request<ImportCheck>(`${c(id)}/characters/import-check`, json("POST", { character_id: characterId }));
export const importCharacter = (id: string, characterId: string) =>
  request<CampaignCharacter>(`${c(id)}/characters`, json("POST", { character_id: characterId, accept_removals: true }));
export const removeCampaignCharacter = (id: string, characterId: string) =>
  request<void>(`${c(id)}/characters/${characterId}`, { method: "DELETE" });

// Notes
export const getNoteTree = (id: string) => request<NoteTree>(`${c(id)}/notes`);
export const getNote = (id: string, noteId: string) => request<CampaignNote>(`${c(id)}/notes/${noteId}`);
export const searchNotes = (id: string, q: string) => request<NoteSearchResult[]>(`${c(id)}/notes/search?q=${encodeURIComponent(q)}`);
export const createNote = (id: string, body: { folder_id?: string; title: string; body?: string }) =>
  request<CampaignNote>(`${c(id)}/notes`, json("POST", body));
export const updateNote = (id: string, noteId: string, body: { folder_id?: string; title?: string; body?: string }) =>
  request<CampaignNote>(`${c(id)}/notes/${noteId}`, json("PATCH", body));
export const deleteNote = (id: string, noteId: string) => request<void>(`${c(id)}/notes/${noteId}`, { method: "DELETE" });
export const createFolder = (id: string, body: { parent_id?: string; name: string }) =>
  request<NoteTree>(`${c(id)}/note-folders`, json("POST", body));
export const updateFolder = (id: string, folderId: string, body: { parent_id?: string; name?: string }) =>
  request<NoteTree>(`${c(id)}/note-folders/${folderId}`, json("PATCH", body));
export const deleteFolder = (id: string, folderId: string) => request<NoteTree>(`${c(id)}/note-folders/${folderId}`, { method: "DELETE" });

// Chat
export const getMessages = (id: string, before?: string | null, limit = 50) =>
  request<ChatPage>(`${c(id)}/messages?limit=${limit}${before ? `&before=${before}` : ""}`);
export const sendMessage = (id: string, body: { body: string; whisper?: boolean; whisper_to?: string[] }) =>
  request<ChatMessage>(`${c(id)}/messages`, json("POST", body));
export const sendRoll = (id: string, body: {
  formula?: string; label?: string; skill_rating?: number; modifier?: number; whisper?: boolean; whisper_to?: string[];
}) => request<ChatMessage>(`${c(id)}/rolls`, json("POST", body));
export const sendCombatEvent = (id: string, body: {
  event: string; summary: string; details: Record<string, unknown>; whisper?: boolean; whisper_to?: string[];
}) => request<ChatMessage>(`${c(id)}/combat-events`, json("POST", body));
export const deleteMessage = (id: string, messageId: string) => request<void>(`${c(id)}/messages/${messageId}`, { method: "DELETE" });
export const markChatRead = (id: string) => request<{ unreadCount: number }>(`${c(id)}/messages/read`, { method: "POST" });

/**
 * Follows the chat live (server-sent events read with fetch, so the access token goes in the header). Calls
 * {@code onEvent} for message / edited / deleted, and reconnects after the stream ends (token expiry, network)
 * until the returned function is called.
 */
export function followChat(id: string, onEvent: (type: string, data: unknown) => void, onReconnect: () => void): () => void {
  let stopped = false;
  let controller: AbortController | null = null;
  const run = async () => {
    let first = true;
    while (!stopped) {
      controller = new AbortController();
      try {
        const response = await authorizedFetch(`${c(id)}/messages/stream`, {
          headers: { Accept: "text/event-stream" },
          signal: controller.signal,
        });
        if (!response.ok || !response.body) throw new Error(String(response.status));
        if (!first) onReconnect();
        first = false;
        const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
        let buffer = "";
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += value;
          let end: number;
          while ((end = buffer.indexOf("\n\n")) >= 0) {
            const block = buffer.slice(0, end);
            buffer = buffer.slice(end + 2);
            let type = "message";
            let data = "";
            for (const line of block.split("\n")) {
              if (line.startsWith("event:")) type = line.slice(6).trim();
              else if (line.startsWith("data:")) data += line.slice(5).trim();
            }
            if (data) {
              try {
                onEvent(type, JSON.parse(data));
              } catch {
                // Ignore a broken event.
              }
            }
          }
        }
      } catch {
        if (stopped) return;
      }
      if (!stopped) await new Promise((resolve) => window.setTimeout(resolve, 2000));
    }
  };
  void run();
  return () => {
    stopped = true;
    controller?.abort();
  };
}

// Rules
export const listCampaignBooks = (id: string) => request<RuleBook[]>(`${c(id)}/rules/books`);
export const createBook = (id: string, title: string, description: string) =>
  request<RuleBook>(`${c(id)}/rules/books`, json("POST", { title, description }));
export const updateBook = (id: string, bookId: string, body: { title?: string; description?: string }) =>
  request<RuleBook>(`${c(id)}/rules/books/${bookId}`, json("PATCH", body));
export const deleteBook = (id: string, bookId: string) => request<void>(`${c(id)}/rules/books/${bookId}`, { method: "DELETE" });
export const addChapter = (id: string, bookId: string, body: { title?: string; chapter_id?: string }) =>
  request<RuleBook>(`${c(id)}/rules/books/${bookId}/chapters`, json("POST", body));
export const removeChapter = (id: string, bookId: string, chapterId: string) =>
  request<RuleBook>(`${c(id)}/rules/books/${bookId}/chapters/${chapterId}`, { method: "DELETE" });
export const reorderChapters = (id: string, bookId: string, chapterIds: string[]) =>
  request<RuleBook>(`${c(id)}/rules/books/${bookId}/chapter-order`, json("PUT", { chapter_ids: chapterIds }));
export const saveChapter = (id: string, chapterId: string, body: {
  title?: string; description?: string; sections: { id?: string; title: string; level: number; body: string }[];
}) => request<RuleChapter>(`${c(id)}/rules/chapters/${chapterId}`, json("PUT", body));

// Character sheets
export const listCampaignSheets = (id: string) => request<CharacterSheet[]>(`${c(id)}/sheets`);
export const createSheet = (id: string, body: SheetBody) => request<CharacterSheet[]>(`${c(id)}/sheets`, json("POST", body));
export const updateSheet = (id: string, sheetId: string, body: Partial<SheetBody>) =>
  request<CharacterSheet[]>(`${c(id)}/sheets/${sheetId}`, json("PATCH", body));
export const deleteSheet = (id: string, sheetId: string) => request<CharacterSheet[]>(`${c(id)}/sheets/${sheetId}`, { method: "DELETE" });

export interface SheetBody {
  name: string;
  description: string;
  base_body: number;
  base_strength: number;
  base_speed: number;
  base_magic: number;
}

// Content (traits, powers, items, creatures, races, jobs): the compendium endpoints, with the campaign as source.
export type ContentKind = "traits" | "powers" | "items" | "creatures" | "races" | "jobs";
export const createContent = <T,>(kind: ContentKind, body: unknown) => request<T>(`/${kind}`, json("POST", body));
export const updateContent = <T,>(kind: ContentKind, id: string, body: unknown) => request<T>(`/${kind}/${id}`, json("PUT", body));
/** The content endpoints answer DELETE with a plain-text message, so this does not parse the body. */
export async function deleteContent(kind: ContentKind, id: string): Promise<void> {
  const response = await authorizedFetch(`/${kind}/${id}`, { method: "DELETE" });
  if (!response.ok) throw new Error(await errorMessage(response));
}
export function filterContent<T>(kind: ContentKind, body: Record<string, unknown>): Promise<{ content: T[]; totalPages: number }> {
  return request(`/${kind}/filter-by-category`, json("POST", body));
}
