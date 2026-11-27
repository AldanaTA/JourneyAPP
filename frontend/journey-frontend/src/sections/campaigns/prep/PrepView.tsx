import { lazy, Suspense, useState } from "react";
import type { LucideIcon } from "lucide-react";
import { BookOpen, Briefcase, NotebookPen, Package, ScrollText, Settings, Skull, Sparkles, UserRound, Wand2 } from "lucide-react";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import type { ContentTabKind } from "./contentKinds";

const SettingsTab = lazy(() => import("./SettingsTab"));
const NotesBrowser = lazy(() => import("../notes/NotesBrowser"));
const ContentTab = lazy(() => import("./ContentTab"));
const RulesTab = lazy(() => import("./RulesTab"));
const SheetsTab = lazy(() => import("./SheetsTab"));

type TabId = "settings" | "notes" | ContentTabKind | "rules" | "sheets";

const TABS: { id: TabId; label: string; icon: LucideIcon }[] = [
  { id: "settings", label: "Settings", icon: Settings },
  { id: "notes", label: "Notes", icon: NotebookPen },
  { id: "traits", label: "Traits", icon: Sparkles },
  { id: "powers", label: "Powers", icon: Wand2 },
  { id: "items", label: "Items", icon: Package },
  { id: "creatures", label: "Creatures", icon: Skull },
  { id: "races", label: "Races", icon: UserRound },
  { id: "jobs", label: "Jobs", icon: Briefcase },
  { id: "rules", label: "Rules", icon: BookOpen },
  { id: "sheets", label: "Sheets", icon: ScrollText },
];

/** Prep mode: campaign settings, notes, and the campaign's own content, one tab at a time. */
export default function PrepView() {
  const [tab, setTab] = useState<TabId>("settings");

  let content;
  if (tab === "settings") content = <SettingsTab />;
  else if (tab === "notes") content = <NotesBrowser />;
  else if (tab === "rules") content = <RulesTab />;
  else if (tab === "sheets") content = <SheetsTab />;
  else content = <ContentTab key={tab} kind={tab} />;

  return (
    <>
      <main className="flex-1 overflow-y-auto overscroll-contain px-4 pb-28 pt-4">
        <div className="mx-auto max-w-3xl">
          <Suspense
            fallback={
              <div className="flex justify-center py-10 text-slate-500">
                <Spinner className="h-5 w-5" />
              </div>
            }
          >
            {content}
          </Suspense>
        </div>
      </main>
      <nav
        aria-label="Prep sections"
        className="absolute inset-x-0 bottom-0 z-20 border-t border-white/10 bg-[#201a12] px-1 pb-[max(0.25rem,env(safe-area-inset-bottom))] pt-1"
      >
        <div className="mx-auto flex max-w-3xl justify-between overflow-x-auto [scrollbar-width:none]">
          {TABS.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              type="button"
              onClick={() => setTab(id)}
              aria-current={tab === id ? "page" : undefined}
              title={label}
              className={cn(
                "flex min-w-[44px] flex-1 flex-col items-center gap-0.5 rounded-xl px-0.5 py-1.5 text-[9px] font-semibold tracking-wide transition sm:text-[10px]",
                tab === id ? "bg-violet-500 text-white shadow shadow-violet-500/30" : "text-white/70 hover:bg-white/10 hover:text-white",
              )}
            >
              <Icon size={17} />
              <span className="max-w-full truncate">{label}</span>
            </button>
          ))}
        </div>
      </nav>
    </>
  );
}
