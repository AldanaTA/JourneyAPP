import { useEffect, useState } from "react";
import { Check, Copy, Crown, Lock, RefreshCw, Shield, Trash2, UserMinus, UserRound } from "lucide-react";
import { useNavigate } from "react-router-dom";
import type { CampaignMember } from "@/apis/campaignApi";
import {
  deleteCampaign,
  removeMember,
  resetJoinCode,
  setAllowedSources,
  setMemberRole,
  transferOwnership,
  updateCampaign,
} from "@/apis/campaignApi";
import { getContentSources } from "@/apis/characterApi";
import type { ContentSource } from "@/models/Character";
import { copyText } from "@/lib/copyText";
import { cn } from "@/lib/utils";
import { ConfirmDialog, Panel, PrimaryButton, SecondaryButton, TextField } from "../../characters/editor/ui";
import { useCampaign, useCampaignStore } from "../campaignStore";

/** Campaign settings: details, invite codes, allowed sources, players and co-storytellers. */
export default function SettingsTab() {
  const campaign = useCampaign();
  const isOwner = campaign.role === "OWNER";
  return (
    <div className="space-y-4">
      <DetailsPanel />
      <InvitePanel />
      <SourcesPanel />
      <MembersPanel />
      {isOwner && <DangerPanel />}
    </div>
  );
}

function useAction() {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const run = async (work: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await work();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Something went wrong");
    } finally {
      setBusy(false);
    }
  };
  return { error, busy, run };
}

function ErrorLine({ error }: { error: string | null }) {
  return error ? <p className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p> : null;
}

function DetailsPanel() {
  const campaign = useCampaign();
  const setCampaign = useCampaignStore((state) => state.setCampaign);
  const [name, setName] = useState(campaign.name);
  const [description, setDescription] = useState(campaign.description);
  const { error, busy, run } = useAction();
  const changed = name !== campaign.name || description !== campaign.description;
  return (
    <Panel title="Campaign">
      <div className="space-y-3">
        <TextField label="Name" value={name} onChange={setName} />
        <TextField label="Description" value={description} onChange={setDescription} multiline />
        <div className="flex justify-end">
          <PrimaryButton
            disabled={!changed || busy || !name.trim()}
            onClick={() => run(async () => setCampaign(await updateCampaign(campaign.id, { name: name.trim(), description })))}
          >
            Save
          </PrimaryButton>
        </div>
      </div>
      <ErrorLine error={error} />
    </Panel>
  );
}

function CodeRow({ label, hint, code, onReset, busy }: { label: string; hint: string; code: string; onReset: () => void; busy: boolean }) {
  const [copied, setCopied] = useState<"code" | "link" | null>(null);
  const [manual, setManual] = useState<string | null>(null);
  const link = `${window.location.origin}/join/${code}`;
  const copy = async (what: "code" | "link") => {
    const text = what === "code" ? code : link;
    if (await copyText(text)) {
      setManual(null);
      setCopied(what);
      window.setTimeout(() => setCopied(null), 1500);
    } else {
      // The browser would not copy: show it selected so it can be copied by hand.
      setManual(text);
    }
  };
  return (
    <div className="rounded-xl border border-slate-200 p-3">
      <div className="text-sm font-semibold text-slate-800">{label}</div>
      <div className="text-xs text-slate-500">{hint}</div>
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <code className="select-all rounded-lg bg-slate-100 px-2.5 py-1.5 font-mono text-sm tracking-wide text-slate-900">{code}</code>
        <SecondaryButton className="px-2.5 py-1.5" onClick={() => void copy("code")}>
          {copied === "code" ? <Check size={14} /> : <Copy size={14} />} {copied === "code" ? "Copied" : "Code"}
        </SecondaryButton>
        <SecondaryButton className="px-2.5 py-1.5" onClick={() => void copy("link")}>
          {copied === "link" ? <Check size={14} /> : <Copy size={14} />} {copied === "link" ? "Copied" : "Link"}
        </SecondaryButton>
        <SecondaryButton className="px-2.5 py-1.5" disabled={busy} onClick={onReset} title="Make a new code; the old one stops working">
          <RefreshCw size={14} /> New code
        </SecondaryButton>
      </div>
      {manual && (
        <div className="mt-2 space-y-1">
          <p className="text-xs text-amber-700">This browser would not copy it. Select the text below and copy it yourself:</p>
          <input
            readOnly
            value={manual}
            autoFocus
            onFocus={(event) => event.currentTarget.select()}
            aria-label="Text to copy"
            className="w-full rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 font-mono text-sm"
          />
        </div>
      )}
    </div>
  );
}

function InvitePanel() {
  const campaign = useCampaign();
  const setCampaign = useCampaignStore((state) => state.setCampaign);
  const { error, busy, run } = useAction();
  return (
    <Panel
      title="Invite"
      action={
        <label className="flex items-center gap-2 text-xs font-medium text-slate-600">
          Taking new players
          <input
            type="checkbox"
            checked={campaign.joinEnabled}
            disabled={busy}
            onChange={(event) => run(async () => setCampaign(await updateCampaign(campaign.id, { join_enabled: event.target.checked })))}
            className="h-4 w-4 accent-violet-600"
          />
        </label>
      }
    >
      <div className="space-y-2">
        {campaign.joinCode && (
          <CodeRow
            label="Player code"
            hint={campaign.joinEnabled ? "Joins as a player." : "Joining is closed for players right now."}
            code={campaign.joinCode}
            busy={busy}
            onReset={() => run(async () => setCampaign(await resetJoinCode(campaign.id, "PLAYER")))}
          />
        )}
        {campaign.storytellerJoinCode && (
          <CodeRow
            label="Co-storyteller code"
            hint="Joins as a co-storyteller. Only you see this code; share it with care."
            code={campaign.storytellerJoinCode}
            busy={busy}
            onReset={() => run(async () => setCampaign(await resetJoinCode(campaign.id, "STORYTELLER")))}
          />
        )}
      </div>
      <ErrorLine error={error} />
    </Panel>
  );
}

function SourcesPanel() {
  const campaign = useCampaign();
  const setCampaign = useCampaignStore((state) => state.setCampaign);
  const [sources, setSources] = useState<ContentSource[] | null>(null);
  const [chosen, setChosen] = useState<string[]>(campaign.allowedSources.map((s) => s.id));
  const { error, busy, run } = useAction();

  useEffect(() => {
    getContentSources().then(setSources).catch(() => setSources([]));
  }, []);

  const saved = campaign.allowedSources.map((s) => s.id).sort().join();
  const changed = [...chosen].sort().join() !== saved;
  const list = (sources ?? []).filter((s) => s.id !== campaign.id);

  return (
    <Panel title="Allowed sources">
      <p className="mb-2 text-xs text-slate-500">
        Where players' characters can take races, jobs, traits, powers, and items from. The core rules and this campaign's
        own content are always allowed.
      </p>
      {sources === null ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : (
        <ul className="space-y-1.5">
          {list.map((source) => {
            const core = source.kind === "core";
            const on = core || chosen.includes(source.id);
            return (
              <li key={source.id}>
                <button
                  type="button"
                  disabled={core || busy}
                  onClick={() => setChosen(on ? chosen.filter((id) => id !== source.id) : [...chosen, source.id])}
                  className={cn(
                    "flex w-full items-center gap-3 rounded-xl border px-3 py-2 text-left",
                    on ? "border-violet-300 bg-violet-50" : "border-slate-200 bg-white",
                  )}
                >
                  <span
                    className={cn(
                      "flex h-5 w-5 shrink-0 items-center justify-center rounded-md border",
                      on ? "border-violet-600 bg-violet-600 text-white" : "border-slate-300",
                    )}
                  >
                    {core ? <Lock size={11} /> : on && <Check size={13} strokeWidth={3} />}
                  </span>
                  <span className="flex-1 text-sm font-medium text-slate-900">{source.name}</span>
                  <span className="text-xs text-slate-500">{core ? "Always on" : source.kind === "campaign" ? "Campaign" : "Book"}</span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
      <div className="mt-3 flex justify-end">
        <PrimaryButton disabled={!changed || busy} onClick={() => run(async () => setCampaign(await setAllowedSources(campaign.id, chosen)))}>
          Save sources
        </PrimaryButton>
      </div>
      <ErrorLine error={error} />
    </Panel>
  );
}

function MembersPanel() {
  const campaign = useCampaign();
  const members = useCampaignStore((state) => state.members);
  const setMembers = useCampaignStore((state) => state.setMembers);
  const reloadMembers = useCampaignStore((state) => state.reloadMembers);
  const load = useCampaignStore((state) => state.load);
  const { error, busy, run } = useAction();
  const [confirm, setConfirm] = useState<{ kind: "remove" | "owner"; member: CampaignMember } | null>(null);
  const isOwner = campaign.role === "OWNER";

  const storytellers = members.filter((m) => m.role !== "PLAYER");
  const players = members.filter((m) => m.role === "PLAYER");

  const row = (m: CampaignMember) => (
    <li key={m.userId} className="flex items-center gap-3 py-2">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-100 text-slate-600">
        {m.role === "OWNER" ? <Crown size={16} /> : m.role === "STORYTELLER" ? <Shield size={16} /> : <UserRound size={16} />}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium text-slate-900">{m.displayName}</span>
        <span className="block text-xs text-slate-500">
          {m.role === "OWNER" ? "Owner" : m.role === "STORYTELLER" ? "Co-storyteller" : "Player"} · {m.characterCount} character
          {m.characterCount === 1 ? "" : "s"}
        </span>
      </span>
      {m.role !== "OWNER" && (
        <span className="flex shrink-0 gap-1">
          {isOwner && (
            <SecondaryButton
              className="px-2 py-1 text-xs"
              disabled={busy}
              onClick={() => run(async () => setMembers(await setMemberRole(campaign.id, m.userId, m.role === "PLAYER" ? "STORYTELLER" : "PLAYER")))}
            >
              {m.role === "PLAYER" ? "Make co-ST" : "Make player"}
            </SecondaryButton>
          )}
          {isOwner && (
            <SecondaryButton className="px-2 py-1 text-xs" disabled={busy} onClick={() => setConfirm({ kind: "owner", member: m })}>
              <Crown size={13} />
            </SecondaryButton>
          )}
          {(isOwner || m.role === "PLAYER") && (
            <button
              type="button"
              aria-label={`Remove ${m.displayName}`}
              title="Remove from the campaign"
              onClick={() => setConfirm({ kind: "remove", member: m })}
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-red-100 text-red-600 hover:bg-red-50"
            >
              <UserMinus size={15} />
            </button>
          )}
        </span>
      )}
    </li>
  );

  return (
    <Panel title="Storytellers and players">
      <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Storytellers</h4>
      <ul className="divide-y divide-slate-100">{storytellers.map(row)}</ul>
      <h4 className="mt-3 text-xs font-semibold uppercase tracking-wide text-slate-500">Players</h4>
      {players.length === 0 ? (
        <p className="py-2 text-sm text-slate-500">No players yet. Share the player code.</p>
      ) : (
        <ul className="divide-y divide-slate-100">{players.map(row)}</ul>
      )}
      <ErrorLine error={error} />
      {confirm?.kind === "remove" && (
        <ConfirmDialog
          title={`Remove ${confirm.member.displayName}?`}
          message="They leave the campaign. Their campaign characters stay theirs, in case they come back."
          confirmLabel="Remove"
          onClose={() => setConfirm(null)}
          onConfirm={() => {
            const member = confirm.member;
            setConfirm(null);
            void run(async () => {
              await removeMember(campaign.id, member.userId);
              await reloadMembers();
            });
          }}
        />
      )}
      {confirm?.kind === "owner" && (
        <ConfirmDialog
          title={`Hand the campaign to ${confirm.member.displayName}?`}
          message="They become the owner; you stay on as a co-storyteller."
          confirmLabel="Hand over"
          onClose={() => setConfirm(null)}
          onConfirm={() => {
            const member = confirm.member;
            setConfirm(null);
            void run(async () => {
              await transferOwnership(campaign.id, member.userId);
              await load(campaign.id);
            });
          }}
        />
      )}
    </Panel>
  );
}

function DangerPanel() {
  const campaign = useCampaign();
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(false);
  const { error, run } = useAction();
  return (
    <Panel title="Delete campaign">
      <p className="text-sm text-slate-600">
        Deletes the campaign with its content, rules, notes, chat, and the campaign versions of characters. Players keep their
        own characters.
      </p>
      <div className="mt-3 flex justify-end">
        <button
          type="button"
          onClick={() => setConfirming(true)}
          className="flex items-center gap-1.5 rounded-lg bg-red-600 px-3.5 py-2 text-sm font-semibold text-white hover:bg-red-700"
        >
          <Trash2 size={15} /> Delete campaign
        </button>
      </div>
      <ErrorLine error={error} />
      {confirming && (
        <ConfirmDialog
          title={`Delete ${campaign.name}?`}
          message="This cannot be undone."
          confirmLabel="Delete"
          onClose={() => setConfirming(false)}
          onConfirm={() => {
            setConfirming(false);
            void run(async () => {
              await deleteCampaign(campaign.id);
              navigate("/campaigns");
            });
          }}
        />
      )}
    </Panel>
  );
}
