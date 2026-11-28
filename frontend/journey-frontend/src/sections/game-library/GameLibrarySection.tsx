import { RuleBookShelf } from "./rules/RuleBookShelf"

function GameLibrarySection() {
  return (
    <div className="space-y-5">
      <header className="flex items-end justify-between gap-3 pt-1">
        <div>
          <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">
            Journey collection
          </p>
          <h2 className="m-0 text-3xl font-semibold tracking-tight text-slate-900">Library</h2>
        </div>
      </header>
      <RuleBookShelf />
    </div>
  )
}

export default GameLibrarySection
