import { useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { Mail } from "lucide-react";
import { requestPasswordReset } from "@/auth/authApi";
import { AuthPageHeader, ErrorBox, Field, NoticeBox, SubmitButton } from "./AuthFormParts";

/**
 * Asks for a password reset email. The confirmation reads the same whether or not an account uses the
 * address, matching the API (so the page cannot be used to find out who has an account).
 */
function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await requestPasswordReset(email.trim());
      setSentTo(email.trim());
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Something went wrong");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-md space-y-5">
      <AuthPageHeader title="Forgot password" />

      <form onSubmit={submit} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        {sentTo ? (
          <NoticeBox
            message={`If an account uses ${sentTo}, we have emailed it a link to choose a new password. The link expires in 30 minutes. Check your spam folder if it does not arrive.`}
          />
        ) : (
          <p className="text-sm text-slate-600">
            Enter the email of your account and we will send you a link to choose a new password.
          </p>
        )}

        <Field
          id="forgot-email"
          label="Email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          autoComplete="email"
          required
        />

        <ErrorBox message={error} />

        <SubmitButton busy={submitting} icon={<Mail size={16} />}>
          {sentTo ? "Send again" : "Send reset link"}
        </SubmitButton>

        <p className="text-center text-sm">
          <Link to="/login" className="font-medium text-violet-700 hover:text-violet-900">Back to sign in</Link>
        </p>
      </form>
    </div>
  );
}

export default ForgotPasswordPage;
