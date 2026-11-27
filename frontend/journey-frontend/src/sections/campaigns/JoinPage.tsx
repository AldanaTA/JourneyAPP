import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import type { JoinPreview } from "@/apis/campaignApi";
import { joinCampaign, previewJoin } from "@/apis/campaignApi";
import { Spinner } from "@/components/ui/spinner";
import { PrimaryButton } from "../characters/editor/ui";

/** Where a shared join link lands: what the campaign is, and a button to join it. */
export default function JoinPage() {
  const { code = "" } = useParams();
  const navigate = useNavigate();
  const [preview, setPreview] = useState<JoinPreview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    previewJoin(code).then(setPreview).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "This link does not work"));
  }, [code]);

  const join = async () => {
    setBusy(true);
    try {
      const campaign = await joinCampaign(code);
      navigate(`/campaigns/${campaign.id}`, { replace: true });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not join");
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-md space-y-4 pt-6">
      {error ? (
        <div className="space-y-2 rounded-2xl border border-slate-200 bg-white p-5 text-center">
          <p className="text-sm text-red-700">{error}</p>
          <Link to="/campaigns" className="text-sm font-medium text-violet-700">Your campaigns</Link>
        </div>
      ) : !preview ? (
        <div className="flex justify-center gap-2 py-10 text-slate-500"><Spinner className="h-5 w-5" /> Loading…</div>
      ) : (
        <div className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">You are invited</p>
          <h2 className="text-2xl font-semibold text-slate-900">{preview.name}</h2>
          <p className="text-sm text-slate-600">Run by {preview.ownerName} · {preview.playerCount} player{preview.playerCount === 1 ? "" : "s"}</p>
          {preview.description && <p className="text-sm text-slate-700">{preview.description}</p>}
          {preview.alreadyMember && !(preview.joinsAs === "STORYTELLER" && preview.currentRole === "PLAYER") ? (
            <PrimaryButton onClick={() => navigate(`/campaigns/${preview.campaignId}`)}>Open campaign</PrimaryButton>
          ) : !preview.joinEnabled ? (
            <p className="text-sm text-amber-700">This campaign is not taking new players right now.</p>
          ) : (
            <PrimaryButton disabled={busy} onClick={() => void join()}>
              Join as {preview.joinsAs === "STORYTELLER" ? "co-storyteller" : "player"}
            </PrimaryButton>
          )}
        </div>
      )}
    </div>
  );
}
