import { Card, CardContent } from "@/components/ui/card"

function CharactersSection() {
  return (
    <div className="space-y-5">
      <header className="flex items-end justify-between gap-3 pt-1">
        <div>
          <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">
            Roster
          </p>
          <h2 className="m-0 text-3xl font-semibold tracking-tight text-slate-900">Characters</h2>
        </div>
      </header>

      <Card className="shadow-sm">
        <CardContent className="gap-3 p-3">
          <div className="flex items-center gap-3 rounded-2xl bg-slate-50 p-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-emerald-400 to-emerald-500 text-sm font-bold text-white">
              A
            </div>
            <div>
              <strong className="block text-base font-semibold text-slate-900">Ari Vale</strong>
              <span className="block text-sm text-slate-600">Ranger • Level 7</span>
            </div>
          </div>
          <div className="flex items-center gap-3 rounded-2xl bg-slate-50 p-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-violet-400 to-violet-500 text-sm font-bold text-white">
              N
            </div>
            <div>
              <strong className="block text-base font-semibold text-slate-900">Nyra Sol</strong>
              <span className="block text-sm text-slate-600">Wizard • Level 6</span>
            </div>
          </div>
          <div className="flex items-center gap-3 rounded-2xl bg-slate-50 p-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-amber-400 to-orange-500 text-sm font-bold text-white">
              T
            </div>
            <div>
              <strong className="block text-base font-semibold text-slate-900">Thorn</strong>
              <span className="block text-sm text-slate-600">Fighter • Level 8</span>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

export default CharactersSection
