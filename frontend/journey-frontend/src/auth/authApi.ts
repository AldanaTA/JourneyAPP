import { useAuthStore } from "./authStore";
import type { AuthResponse } from "./authStore";
import { API_BASE_URL, errorMessage } from "./session";

export interface SignUpRequest {
  username: string;
  password: string;
  displayName?: string;
  email?: string;
}

async function startSession(path: string, body: unknown): Promise<void> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  useAuthStore.getState().setSession((await response.json()) as AuthResponse);
}

/** Signs in with a username (or email) and password. Throws with the server's message on failure. */
export function signIn(login: string, password: string): Promise<void> {
  return startSession("/auth/login", { login, password });
}

/** Creates an account and signs it in. Throws with the server's message (e.g. "That username is taken"). */
export function signUp(request: SignUpRequest): Promise<void> {
  return startSession("/auth/register", {
    username: request.username,
    password: request.password,
    display_name: request.displayName || null,
    email: request.email || null,
  });
}

/** Ends the session on the server (its tokens stop working) and locally. */
export async function signOut(): Promise<void> {
  try {
    await fetch(`${API_BASE_URL}/auth/logout`, { method: "POST", credentials: "same-origin" });
  } finally {
    useAuthStore.getState().clearSession();
  }
}
