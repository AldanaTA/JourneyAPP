import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardTitle } from "@/components/ui/card"

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
        <Button type="button" className="rounded-xl bg-slate-900 text-white hover:bg-slate-800">
          + New
        </Button>
      </header>

      <Card className="border-violet-200/70 bg-gradient-to-br from-violet-100/80 to-amber-50 shadow-sm">
        <CardContent className="gap-3 p-4">
          <Badge className="bg-violet-100 text-violet-700 hover:bg-violet-100">
            Active campaign
          </Badge>
          <CardTitle className="mt-1 text-xl">Moonfall: The Shattered Crown</CardTitle>
          <p className="text-sm leading-6 text-slate-600">
            4 sessions logged • 1 party roster • 3 unresolved mysteries
          </p>
        </CardContent>
      </Card>

      <div className="grid grid-cols-2 gap-3.5">
        <Card className="shadow-sm">
          <CardContent className="gap-3 p-4">
            <Badge variant="secondary">System</Badge>
            <CardTitle className="text-lg">Dungeon Crawl</CardTitle>
            <p className="text-sm text-slate-600">Core rules • 3 sessions</p>
          </CardContent>
        </Card>
        <Card className="shadow-sm">
          <CardContent className="gap-3 p-4">
            <Badge variant="secondary">System</Badge>
            <CardTitle className="text-lg">Shadowdark</CardTitle>
            <p className="text-sm text-slate-600">Dark fantasy • 2 parties</p>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

export default GameLibrarySection
