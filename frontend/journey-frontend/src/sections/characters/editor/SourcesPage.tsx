import { useState } from "react";
import { BookOpen, Check, Lock, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { useReferenceStore } from "./referenceStore";
import { PrimaryButton } from "./ui";

/**
 * The first page of character creation: which content sources the character is built from. The core rules are
 * always on; every other source the user can read (books they own, public settings like Calthora, campaigns
 * they belong to) is a row with a checkmark.
 */
export default function SourcesPage({
  initial,
  onCancel,
  onContinue,
}: {
  initial: string[];
  onCancel: () => void;
  onContinue: (sourceIds: string[]) => void;
}) {
  const sources = useReferenceStore((state) => state.sources);
  const [chosen, setChosen] = useState<string[]>(() => {
    if (initial.length) return initial;
    // Calthora, the base setting, starts checked.
    return sources.filter((s) => s.kind === "book" && s.name === "Calthora").map((s) => s.id);
  });
  const toggle = (id: string) => setChosen(chosen.includes(id) ? chosen.filter((c) => c !== id) : [...chosen, id]);

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center gap-3 bg-[#201a12] px-3 py-3 text-white">
        <button
          type="button"
          onClick={onCancel}
          aria-label="Cancel character creation"
          className="flex h-9 w-9 items-center justify-center rounded-xl border border-white/10 bg-white/5 hover:bg-white/10"
        >
          <X size={20} />
        </button>
        <h1 className="text-base font-semibold">New character</h1>
      </header>

      <main className="flex-1 overflow-y-auto px-4 py-6">
        <div className="mx-auto max-w-xl space-y-5">
          <div>
            <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">Step 1</p>
            <h2 className="text-2xl font-semibold tracking-tight text-slate-900">Choose your content</h2>
            <p className="mt-1 text-sm text-slate-600">
              Races, jobs, traits, powers, and items will come from the sources you pick here.
            </p>
          </div>

          <ul className="space-y-2">
            {sources.map((source) => {
              const locked = source.kind === "core";
              const on = locked || chosen.includes(source.id);
              return (
                <li key={source.id}>
                  <div
                    className={cn(
                      "flex items-center gap-3 rounded-2xl border bg-white p-3.5 shadow-sm transition",
                      on ? "border-violet-300" : "border-slate-200",
                    )}
                  >
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet-100 text-violet-700">
                      <BookOpen size={18} />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block font-semibold text-slate-900">{source.name}</span>
                      <span className="block truncate text-xs text-slate-500">
                        {locked ? "Core rules · always included" : source.kind === "campaign" ? "Campaign" : "Book"}
                      </span>
                    </span>
                    <button
                      type="button"
                      onClick={() => !locked && toggle(source.id)}
                      disabled={locked}
                      aria-pressed={on}
                      aria-label={locked ? `${source.name} is always included` : `${on ? "Remove" : "Add"} ${source.name}`}
                      className={cn(
                        "flex h-9 w-9 shrink-0 items-center justify-center rounded-full border-2 transition",
                        on ? "border-violet-600 bg-violet-600 text-white" : "border-slate-300 bg-white text-transparent hover:border-violet-400",
                        locked && "opacity-70",
                      )}
                    >
                      {locked ? <Lock size={14} className="text-white" /> : <Check size={18} strokeWidth={3} />}
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>

          <div className="flex justify-end">
            <PrimaryButton onClick={() => onContinue(chosen)} className="px-5 py-2.5">
              Continue
            </PrimaryButton>
          </div>
        </div>
      </main>
    </div>
  );
}
