import { lazy, Suspense, useEffect } from "react";
import { ArrowLeft, Hammer, Swords } from "lucide-react";
import { createPortal } from "react-dom";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import { isStoryteller, useCampaignStore } from "./campaignStore";

const PrepView = lazy(() => import("./prep/PrepView"));
const PlayView = lazy(() => import("./play/PlayView"));
const PlayerHome = lazy(() => import("./PlayerHome"));

function Loading() {
  return (
    <div className="m-auto flex items-center gap-2 text-slate-500">
      <Spinner className="h-5 w-5" /> Loading…
    </div>
  );
}

/**
 * One campaign, in a full-screen window. Storytellers switch between Prep (settings, notes, and the campaign's
 * content) and Play (windows with search, players, chat, and notes); a new campaign opens in Prep. Players see
 * their home: bring in a character or create one.
 */
export default function CampaignPage() {
  const { campaignId = "" } = useParams();
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const campaign = useCampaignStore((state) => state.campaign);
  const error = useCampaignStore((state) => state.error);
  const load = useCampaignStore((state) => state.load);
  const clear = useCampaignStore((state) => state.clear);
  const mode = params.get("mode") === "play" ? "play" : "prep";

  useEffect(() => {
    void load(campaignId);
    return () => clear();
  }, [campaignId, load, clear]);

  // Keep the page behind from scrolling.
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  const storyteller = campaign ? isStoryteller(campaign) : false;
  const ready = campaign?.id === campaignId;

  return createPortal(
    <div className="fixed inset-0 z-40 flex flex-col bg-[#f5efe7] text-slate-900">
      <header className="z-20 flex items-center gap-2 bg-[#201a12] px-3 py-2.5 text-white">
        <button
          type="button"
          onClick={() => navigate("/campaigns")}
          aria-label="Back to campaigns"
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-white/10 bg-white/5 hover:bg-white/10"
        >
          <ArrowLeft size={20} />
        </button>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-semibold">{ready ? campaign?.name : "Campaign"}</div>
          {ready && <div className="truncate text-[11px] text-white/60">{storyteller ? (mode === "play" ? "Play mode" : "Prep mode") : "Player"}</div>}
        </div>
        {ready && storyteller && (
          <div className="flex rounded-xl bg-white/10 p-1" role="tablist" aria-label="Mode">
            {(
              [
                ["prep", "Prep", Hammer],
                ["play", "Play", Swords],
              ] as const
            ).map(([value, label, Icon]) => (
              <button
                key={value}
                type="button"
                role="tab"
                aria-selected={mode === value}
                onClick={() => setParams(value === "play" ? { mode: "play" } : {}, { replace: true })}
                className={cn(
                  "flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition",
                  mode === value ? "bg-white text-[#201a12]" : "text-white/75 hover:text-white",
                )}
              >
                <Icon size={14} /> {label}
              </button>
            ))}
          </div>
        )}
      </header>

      {error ? (
        <div className="m-auto max-w-sm space-y-3 p-6 text-center">
          <p className="text-sm text-red-700">{error}</p>
          <button type="button" onClick={() => navigate("/campaigns")} className="text-sm font-medium text-violet-700">
            Back to campaigns
          </button>
        </div>
      ) : !ready ? (
        <Loading />
      ) : (
        <Suspense fallback={<Loading />}>{storyteller ? mode === "play" ? <PlayView /> : <PrepView /> : <PlayerHome />}</Suspense>
      )}
    </div>,
    document.body,
  );
}
