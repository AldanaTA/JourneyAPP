/**
 * Keeps the sign-in session alive and authenticates requests. State lives in {@link useAuthStore}.
 *
 * - The refresh token is an HttpOnly cookie the browser sends only to /api/v1/auth.
 * - The access token is refreshed a minute before it expires, and again whenever a request comes back 401.
 * - Refreshes are single-flight: concurrent callers (StrictMode double effects, parallel requests) share one
 *   request, because the server rotates the refresh token on every use.
 */
import { useAuthStore } from "./authStore";
import type { AuthResponse } from "./authStore";

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "/api/v1").replace(/\/$/, "");
const REFRESH_EARLY_MS = 60_000;
const MIN_REFRESH_DELAY_MS = 5_000;
/** Another tab may have just rotated the cookie; one retry after this delay picks up the new one. */
const REFRESH_RETRY_DELAY_MS = 1_000;

let refreshTimer: number | undefined;
let refreshInFlight: Promise<boolean> | null = null;

// Re-arm the early refresh whenever the token changes (sign-in, refresh) and stop it on sign-out.
useAuthStore.subscribe((state, previous) => {
  if (state.expiresAt === previous.expiresAt) return;
  window.clearTimeout(refreshTimer);
  if (state.expiresAt !== null) {
    const delay = Math.max(state.expiresAt - Date.now() - REFRESH_EARLY_MS, MIN_REFRESH_DELAY_MS);
    refreshTimer = window.setTimeout(() => void refreshSession(), delay);
  }
});

/** Reads the message from an API error (RFC 9457 problem details), falling back to the status text. */
export async function errorMessage(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json();
    if (body && typeof body === "object" && "detail" in body && typeof body.detail === "string") {
      return body.detail;
    }
  } catch {
    // Not JSON; use the status below.
  }
  return response.statusText || `Request failed (${response.status})`;
}

function requestRefresh(): Promise<Response> {
  return fetch(`${API_BASE_URL}/auth/refresh`, { method: "POST", credentials: "same-origin" });
}

/**
 * Exchanges the refresh cookie for a new access token. Resolves true when signed in afterwards.
 * Concurrent calls share one request.
 */
export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const { setSession, clearSession } = useAuthStore.getState();
      try {
        let response = await requestRefresh();
        if (response.status === 401 && useAuthStore.getState().status === "signedIn") {
          await new Promise((resolve) => window.setTimeout(resolve, REFRESH_RETRY_DELAY_MS));
          response = await requestRefresh();
        }
        if (!response.ok) {
          clearSession();
          return false;
        }
        setSession((await response.json()) as AuthResponse);
        return true;
      } catch {
        // Network error: keep a live session (the next request or timer tries again); otherwise signed out.
        if (useAuthStore.getState().status === "loading") clearSession();
        return useAuthStore.getState().status === "signedIn";
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

/**
 * fetch() for endpoints that need the signed-in user: adds the access token, and on a 401 refreshes the
 * session once and retries. Paths are relative to the API base URL ("/characters").
 */
export async function authorizedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const send = () => {
    const headers = new Headers(init.headers);
    const { accessToken } = useAuthStore.getState();
    if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
    return fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  };
  let response = await send();
  if (response.status === 401 && (await refreshSession())) {
    response = await send();
  }
  return response;
}
