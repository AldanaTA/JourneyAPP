import { create } from "zustand";
import { createCharacter, saveCharacter } from "@/apis/characterApi";
import type { Draft } from "./draft";
import { draftFromServer, toSaveBody } from "./draft";

/**
 * The character being created, edited, or viewed. Changes stay in the browser and bump {@link version};
 * {@link save} sends the whole character in one request (on Save and on the editor's periodic autosave).
 */
interface DraftState {
  draft: Draft | null;
  /** True when viewing: nothing can be changed. */
  readOnly: boolean;
  /** Counts changes; equal to savedVersion when everything is saved. */
  version: number;
  savedVersion: number;
  saving: boolean;
  saveError: string | null;
  /** False while a save error waits to be read (shown as a popup). */
  saveErrorSeen: boolean;
  /** The version a save last failed at: autosave waits for another change before trying again. */
  failedVersion: number | null;
  lastSavedAt: number | null;

  /** Starts editing (or viewing) a character. {@code saved} is false for a new character. */
  open: (draft: Draft, readOnly: boolean, saved: boolean) => void;
  close: () => void;
  /** Changes the draft: {@code recipe} edits a copy in place. */
  update: (recipe: (draft: Draft) => void) => void;
  /** Sets something fetched for display (race traits, compendium values) without counting as a change. */
  patchQuietly: (recipe: (draft: Draft) => void) => void;
  save: () => Promise<boolean>;
  /** Closes the save error popup (true) or shows it again (false). */
  setSaveErrorSeen: (seen: boolean) => void;
}

export const useDraftStore = create<DraftState>((set, get) => ({
  draft: null,
  readOnly: false,
  version: 0,
  savedVersion: 0,
  saving: false,
  saveError: null,
  saveErrorSeen: true,
  failedVersion: null,
  lastSavedAt: null,

  open: (draft, readOnly, saved) =>
    set({
      draft,
      readOnly,
      version: saved ? 0 : 1,
      savedVersion: 0,
      saving: false,
      saveError: null,
      failedVersion: null,
      lastSavedAt: null,
    }),

  close: () => set({ draft: null, version: 0, savedVersion: 0, saveError: null, failedVersion: null }),

  update: (recipe) => {
    const { draft, readOnly, version } = get();
    if (!draft || readOnly) return;
    const next = structuredClone(draft);
    recipe(next);
    set({ draft: next, version: version + 1 });
  },

  patchQuietly: (recipe) => {
    const { draft } = get();
    if (!draft) return;
    const next = structuredClone(draft);
    recipe(next);
    set({ draft: next });
  },

  setSaveErrorSeen: (seen) => set({ saveErrorSeen: seen }),

  save: async () => {
    const { draft, saving, readOnly, version } = get();
    if (!draft || saving || readOnly) return false;
    set({ saving: true, saveError: null });
    try {
      let id = draft.id;
      if (!id) {
        const created = await createCharacter(draft.name.trim() || "Unnamed character", draft.sourceIds);
        id = created.id;
        // Keep the id even if the rest of the save fails, so the next save does not create another character.
        set((state) => ({ draft: state.draft ? { ...state.draft, id } : state.draft }));
      }
      const saved = await saveCharacter(id, toSaveBody({ ...draft, id }));
      const current = get();
      if (current.version === version) {
        // Nothing changed while saving: take the server's version (fresh ids, derived values).
        set({ draft: draftFromServer(saved.character, saved.inventory, current.draft), savedVersion: version });
      } else {
        // The player kept editing: keep their changes; they are saved next time.
        set((state) => ({ savedVersion: version, draft: state.draft ? { ...state.draft, status: saved.character.status } : null }));
      }
      set({ saving: false, lastSavedAt: Date.now(), failedVersion: null });
      return true;
    } catch (error) {
      set({
        saving: false,
        saveError: error instanceof Error ? error.message : "Could not save",
        saveErrorSeen: false,
        failedVersion: version,
      });
      return false;
    }
  },
}));

/** True when there are changes that are not saved yet. */
export function useIsDirty(): boolean {
  return useDraftStore((state) => state.version !== state.savedVersion);
}
