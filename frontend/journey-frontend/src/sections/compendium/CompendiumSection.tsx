import { BookOpenText, Library, ScrollText } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"

function CompendiumSection() {
  return (
    <div className="space-y-5">
      <header className="flex items-end justify-between gap-3 pt-1">
        <div>
          <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">
            Reference
          </p>
          <h2 className="m-0 text-3xl font-semibold tracking-tight text-slate-900">Compendium</h2>
        </div>
      </header>

      <Card className="shadow-sm">
        <CardContent className="gap-3 p-3">
          <div className="flex items-center gap-3 rounded-2xl bg-slate-50 p-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-100 text-violet-700">
              <ScrollText size={18} />
            </div>
            <div>
              <strong className="block text-base font-semibold text-slate-900">Bestiary</strong>
              <span className="block text-sm text-slate-600">Creatures and monsters</span>
            </div>
          </div>
          <div className="flex items-center gap-3 rounded-2xl bg-slate-50 p-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-100 text-violet-700">
              <BookOpenText size={18} />
            </div>
            <div>
              <strong className="block text-base font-semibold text-slate-900">Magic Items</strong>
              <span className="block text-sm text-slate-600">Relics and enchanted gear</span>
            </div>
          </div>
          <div className="flex items-center gap-3 rounded-2xl bg-slate-50 p-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-100 text-violet-700">
              <Library size={18} />
            </div>
            <div>
              <strong className="block text-base font-semibold text-slate-900">World Lore</strong>
              <span className="block text-sm text-slate-600">History, factions, and maps</span>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

export default CompendiumSection
