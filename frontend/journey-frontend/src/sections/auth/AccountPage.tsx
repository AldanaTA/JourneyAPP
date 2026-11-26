import { useState } from "react";
import type { FormEvent } from "react";
import { KeyRound } from "lucide-react";
import { changePassword } from "@/auth/authApi";
import { useAuthStore } from "@/auth/authStore";
import { AuthPageHeader, ErrorBox, Field, NoticeBox, SubmitButton } from "./AuthFormParts";
import { MIN_PASSWORD_LENGTH } from "./formStyles";

/** Account settings: who is signed in, and changing the password. */
function AccountPage() {
  const user = useAuthStore((state) => state.user);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const mismatch = confirmPassword !== "" && confirmPassword !== newPassword;

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (newPassword !== confirmPassword) {
      setError("The new passwords do not match");
      return;
    }
    setSubmitting(true);
    setError(null);
    setNotice(null);
    try {
      await changePassword(currentPassword, newPassword);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setNotice("Password changed. Your other devices were signed out.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Something went wrong");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-md space-y-5">
      <AuthPageHeader title="Account settings" eyebrow="Settings" />

      {user && (
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 rounded-2xl border border-slate-200 bg-white p-5 text-sm shadow-sm">
          <dt className="text-slate-500">Name</dt>
          <dd className="font-medium text-slate-900">{user.displayName}</dd>
          <dt className="text-slate-500">Username</dt>
          <dd className="font-medium text-slate-900">{user.username ?? "—"}</dd>
          <dt className="text-slate-500">Email</dt>
          <dd className="font-medium text-slate-900">{user.email ?? "Not set"}</dd>
        </dl>
      )}

      <form onSubmit={submit} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="text-lg font-semibold text-slate-900">Change password</h3>
        <Field
          id="account-current-password"
          label="Current password"
          type="password"
          value={currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
          autoComplete="current-password"
          required
        />
        <Field
          id="account-new-password"
          label="New password"
          hint={`At least ${MIN_PASSWORD_LENGTH} characters.`}
          type="password"
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
          autoComplete="new-password"
          minLength={MIN_PASSWORD_LENGTH}
          required
        />
        <Field
          id="account-confirm-password"
          label="Confirm new password"
          type="password"
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
          autoComplete="new-password"
          problem={mismatch ? "The passwords do not match" : null}
          required
        />

        <ErrorBox message={error} />
        <NoticeBox message={notice} />

        <SubmitButton busy={submitting} icon={<KeyRound size={16} />}>Change password</SubmitButton>
      </form>
    </div>
  );
}

export default AccountPage;
