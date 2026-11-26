import { useState } from "react";
import type { FormEvent } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { LogIn, UserPlus } from "lucide-react";
import { signIn, signUp } from "@/auth/authApi";
import { useAuthStore } from "@/auth/authStore";
import { Spinner } from "@/components/ui/spinner";

type Mode = "signIn" | "signUp";

const inputClass =
  "w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-base text-slate-900 outline-none " +
  "placeholder:text-slate-400 focus:border-violet-400 focus:ring-2 focus:ring-violet-100 sm:text-sm";

/**
 * Sign-in and account creation, the only page signed-out visitors can reach. After success the app switches
 * to the signed-in layout, where this page sends the user on to where they were going (or the Library).
 */
function SignInPage() {
  const status = useAuthStore((state) => state.status);
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/library";

  const [mode, setMode] = useState<Mode>("signIn");
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === "signedIn") {
    return <Navigate to={from} replace />;
  }

  const switchMode = (next: Mode) => {
    setMode(next);
    setError(null);
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      if (mode === "signIn") {
        await signIn(login.trim(), password);
      } else {
        await signUp({ username: login.trim(), password, displayName: displayName.trim(), email: email.trim() });
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Something went wrong");
    } finally {
      setSubmitting(false);
    }
  };

  const signingUp = mode === "signUp";

  return (
    <div className="mx-auto max-w-md space-y-5">
      <header className="space-y-1 border-b border-slate-200 pb-3">
        <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">Account</p>
        <h2 className="text-2xl font-semibold tracking-tight text-slate-900 md:text-3xl">
          {signingUp ? "Create an account" : "Sign in"}
        </h2>
      </header>

      <div className="flex rounded-xl bg-slate-100 p-1.5" role="tablist" aria-label="Sign in or create an account">
        {(["signIn", "signUp"] as const).map((value) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={mode === value}
            onClick={() => switchMode(value)}
            className={`flex-1 rounded-lg px-3 py-2 text-sm font-medium transition ${
              mode === value ? "bg-slate-900 text-white shadow-sm" : "text-slate-600 hover:bg-slate-200/80"
            }`}
          >
            {value === "signIn" ? "Sign in" : "Create account"}
          </button>
        ))}
      </div>

      <form onSubmit={submit} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="space-y-1.5">
          <label htmlFor="auth-login" className="block text-sm font-medium text-slate-700">
            {signingUp ? "Username" : "Username or email"}
          </label>
          <input
            id="auth-login"
            value={login}
            onChange={(event) => setLogin(event.target.value)}
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            required
            className={inputClass}
          />
          {signingUp && <p className="text-xs text-slate-500">3-32 letters, digits, ".", "_" or "-".</p>}
        </div>

        <div className="space-y-1.5">
          <label htmlFor="auth-password" className="block text-sm font-medium text-slate-700">Password</label>
          <input
            id="auth-password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete={signingUp ? "new-password" : "current-password"}
            minLength={signingUp ? 8 : undefined}
            required
            className={inputClass}
          />
          {signingUp && <p className="text-xs text-slate-500">At least 8 characters.</p>}
        </div>

        {signingUp && (
          <>
            <div className="space-y-1.5">
              <label htmlFor="auth-display-name" className="block text-sm font-medium text-slate-700">
                Display name <span className="font-normal text-slate-400">(optional)</span>
              </label>
              <input
                id="auth-display-name"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                autoComplete="nickname"
                maxLength={64}
                className={inputClass}
              />
            </div>
            <div className="space-y-1.5">
              <label htmlFor="auth-email" className="block text-sm font-medium text-slate-700">
                Email <span className="font-normal text-slate-400">(optional, lets you sign in with it)</span>
              </label>
              <input
                id="auth-email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                autoComplete="email"
                className={inputClass}
              />
            </div>
          </>
        )}

        {error && (
          <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="flex w-full items-center justify-center gap-2 rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-60"
        >
          {submitting ? <Spinner className="h-4 w-4" /> : signingUp ? <UserPlus size={16} /> : <LogIn size={16} />}
          {signingUp ? "Create account" : "Sign in"}
        </button>
      </form>
    </div>
  );
}

export default SignInPage;
