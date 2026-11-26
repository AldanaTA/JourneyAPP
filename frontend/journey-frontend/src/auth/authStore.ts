import { create } from "zustand";

export interface AuthUser {
  id: string;
  username: string | null;
  displayName: string;
  email: string | null;
  admin: boolean;
}

/** Body returned by sign-up, sign-in, and refresh. */
export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  /** Seconds until the access token expires. */
  expiresIn: number;
  user: AuthUser;
}

/** "loading" until the first refresh on start-up has said whether a session exists. */
export type AuthStatus = "loading" | "signedIn" | "signedOut";

interface AuthStore {
  status: AuthStatus;
  /** Set exactly when status is "signedIn". */
  user: AuthUser | null;
  /**
   * The short-lived access JWT. Held in memory only: this store is deliberately not persisted, so the token
   * never reaches localStorage. After a reload it is restored from the HttpOnly refresh cookie instead.
   */
  accessToken: string | null;
  /** Epoch milliseconds when accessToken expires. */
  expiresAt: number | null;

  /** Stores a new session from an auth response. */
  setSession: (response: AuthResponse) => void;
  /** Forgets the session (signed out, or the refresh failed). */
  clearSession: () => void;
}

/**
 * The sign-in session. Read it in components with a selector, e.g. {@code useAuthStore((s) => s.user)},
 * and outside React with {@code useAuthStore.getState()}.
 */
export const useAuthStore = create<AuthStore>()((set) => ({
  status: "loading",
  user: null,
  accessToken: null,
  expiresAt: null,

  setSession: (response) =>
    set({
      status: "signedIn",
      user: response.user,
      accessToken: response.accessToken,
      expiresAt: Date.now() + response.expiresIn * 1000,
    }),
  clearSession: () => set({ status: "signedOut", user: null, accessToken: null, expiresAt: null }),
}));
