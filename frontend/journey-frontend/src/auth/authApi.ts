import { useAuthStore } from "./authStore";
import type { AuthResponse } from "./authStore";
import { API_BASE_URL, authorizedFetch, errorMessage } from "./session";

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

/**
 * Changes the signed-in user's password. The server signs out every other device and starts a fresh session
 * for this one, which replaces the current one here.
 */
export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  const response = await authorizedFetch("/auth/password", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ current_password: currentPassword, new_password: newPassword }),
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  useAuthStore.getState().setSession((await response.json()) as AuthResponse);
}

/** Asks for a password reset email. Succeeds whether or not an account uses the address. */
export async function requestPasswordReset(email: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/auth/password/forgot`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email }),
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
}

/**
 * Sets a new password with the token from a reset email. Every session of the account ends, including this
 * browser's, so the user signs in again with the new password.
 */
export async function resetPassword(token: string, newPassword: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/auth/password/reset`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, new_password: newPassword }),
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  useAuthStore.getState().clearSession();
}
