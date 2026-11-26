import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { KeyRound } from "lucide-react";
import { resetPassword } from "@/auth/authApi";
import { AuthPageHeader, ErrorBox, Field, SubmitButton } from "./AuthFormParts";
import { MIN_PASSWORD_LENGTH } from "./formStyles";

/**
 * Opened from the link in a reset email (/reset-password?token=...). Setting the new password signs the
 * account out everywhere, so the user is then sent to sign in with it.
 */
function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const navigate = useNavigate();

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const mismatch = confirmPassword !== "" && confirmPassword !== password;

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (password !== confirmPassword) {
      setError("The passwords do not match");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await resetPassword(token, password);
      navigate("/login", {
        replace: true,
        state: { notice: "Your password was changed. Sign in with your new password." },
      });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Something went wrong");
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-md space-y-5">
      <AuthPageHeader title="Choose a new password" />

      {!token ? (
        <div className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <ErrorBox message="This page needs the link from your reset email." />
          <p className="text-center text-sm">
            <Link to="/forgot-password" className="font-medium text-violet-700 hover:text-violet-900">
              Request a new link
            </Link>
          </p>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <Field
            id="reset-password"
            label="New password"
            hint={`At least ${MIN_PASSWORD_LENGTH} characters.`}
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
            minLength={MIN_PASSWORD_LENGTH}
            required
          />
          <Field
            id="reset-confirm-password"
            label="Confirm new password"
            type="password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            autoComplete="new-password"
            problem={mismatch ? "The passwords do not match" : null}
            required
          />

          <ErrorBox message={error} />
          {error?.includes("expired") && (
            <p className="text-center text-sm">
              <Link to="/forgot-password" className="font-medium text-violet-700 hover:text-violet-900">
                Request a new link
              </Link>
            </p>
          )}

          <SubmitButton busy={submitting} icon={<KeyRound size={16} />}>Set new password</SubmitButton>
        </form>
      )}
    </div>
  );
}

export default ResetPasswordPage;
