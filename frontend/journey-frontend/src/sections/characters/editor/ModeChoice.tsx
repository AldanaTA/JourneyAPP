import { Compass, PenLine, X } from "lucide-react";

/** First step of creating a character: be guided through each part, or go straight to the editor. */
export default function ModeChoice({ onPick, onCancel }: { onPick: (guided: boolean) => void; onCancel: () => void }) {
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
        <div className="mx-auto max-w-xl space-y-4">
          <div>
            <h2 className="text-2xl font-semibold tracking-tight text-slate-900">How do you want to build?</h2>
            <p className="mt-1 text-sm text-slate-600">You can leave the guide at any time and keep going on your own.</p>
          </div>
          <button type="button" onClick={() => onPick(true)}
            className="flex w-full items-start gap-4 rounded-2xl border-2 border-violet-300 bg-white p-4 text-left shadow-sm transition hover:border-violet-500">
            <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-violet-100 text-violet-700"><Compass size={24} /></span>
            <span>
              <span className="block text-lg font-semibold text-slate-900">Guided</span>
              <span className="block text-sm text-slate-600">
                Step by step: your story, sheet, race, job, attributes, skills, traits, powers, and gear, with what each part
                means and your options. Good for a first character.
              </span>
            </span>
          </button>
          <button type="button" onClick={() => onPick(false)}
            className="flex w-full items-start gap-4 rounded-2xl border border-slate-200 bg-white p-4 text-left shadow-sm transition hover:border-violet-300">
            <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-700"><PenLine size={24} /></span>
            <span>
              <span className="block text-lg font-semibold text-slate-900">Unguided</span>
              <span className="block text-sm text-slate-600">The full editor with every tab open. Fill things in any order.</span>
            </span>
          </button>
        </div>
      </main>
    </div>
  );
}
