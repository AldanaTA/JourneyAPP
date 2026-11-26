import { useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate, useLocation } from "react-router-dom";
import { LogIn, UserPlus } from "lucide-react";
import { signIn, signUp } from "@/auth/authApi";
import { useAuthStore } from "@/auth/authStore";
import { AuthPageHeader, ErrorBox, Field, NoticeBox, SubmitButton } from "./AuthFormParts";
import { MIN_PASSWORD_LENGTH } from "./formStyles";

type Mode = "signIn" | "signUp";

/** What other pages may pass when sending the user here. */
interface SignInState {
  /** Where to go after signing in. */
  from?: string;
  /** A message to show, e.g. after a password reset. */
  notice?: string;
}

/**
 * Sign-in and account creation, the only pages signed-out visitors can reach (with password reset). After
 * success the app switches to the signed-in layout, where this page sends the user on to where they were
 * going (or the Library).
 */
function SignInPage() {
  const status = useAuthStore((state) => state.status);
  const location = useLocation();
  const state = (location.state as SignInState | null) ?? {};
  const from = state.from ?? "/library";

  const [mode, setMode] = useState<Mode>("signIn");
  const [login, setLogin] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === "signedIn") {
    return <Navigate to={from} replace />;
  }

  const signingUp = mode === "signUp";
  // Only complain once the user has typed in the confirm box.
  const mismatch = signingUp && confirmPassword !== "" && confirmPassword !== password;

  const switchMode = (next: Mode) => {
    setMode(next);
    setError(null);
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (signingUp && password !== confirmPassword) {
      setError("The passwords do not match");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      if (signingUp) {
        await signUp({ username: login.trim(), email: email.trim(), password, displayName: displayName.trim() });
      } else {
        await signIn(login.trim(), password);
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Something went wrong");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-md space-y-5">
      <AuthPageHeader title={signingUp ? "Create an account" : "Sign in"} />

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

      {!signingUp && <NoticeBox message={state.notice ?? null} />}

      <form onSubmit={submit} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <Field
          id="auth-login"
          label={signingUp ? "Username" : "Username or email"}
          hint={signingUp ? "3-32 letters, digits, \".\", \"_\" or \"-\"." : undefined}
          value={login}
          onChange={(event) => setLogin(event.target.value)}
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          required
        />

        {signingUp && (
          <Field
            id="auth-email"
            label="Email"
            hint="For password resets. You can also sign in with it."
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
            required
          />
        )}

        <Field
          id="auth-password"
          label="Password"
          hint={signingUp ? `At least ${MIN_PASSWORD_LENGTH} characters.` : undefined}
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete={signingUp ? "new-password" : "current-password"}
          minLength={signingUp ? MIN_PASSWORD_LENGTH : undefined}
          required
        />

        {signingUp && (
          <>
            <Field
              id="auth-confirm-password"
              label="Confirm password"
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              autoComplete="new-password"
              problem={mismatch ? "The passwords do not match" : null}
              required
            />
            <Field
              id="auth-display-name"
              label={<>Display name <span className="font-normal text-slate-400">(optional)</span></>}
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              autoComplete="nickname"
              maxLength={64}
            />
          </>
        )}

        <ErrorBox message={error} />

        <SubmitButton busy={submitting} icon={signingUp ? <UserPlus size={16} /> : <LogIn size={16} />}>
          {signingUp ? "Create account" : "Sign in"}
        </SubmitButton>

        {!signingUp && (
          <p className="text-center text-sm">
            <Link to="/forgot-password" className="font-medium text-violet-700 hover:text-violet-900">
              Forgot password?
            </Link>
          </p>
        )}
      </form>
    </div>
  );
}

export default SignInPage;
