import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardTitle } from "@/components/ui/card"

function CampaignsSection() {
  return (
    <div className="space-y-5">
      <header className="flex items-end justify-between gap-3 pt-1">
        <div>
          <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.18em] text-violet-600">
            Sessions
          </p>
          <h2 className="m-0 text-3xl font-semibold tracking-tight text-slate-900">Campaigns</h2>
        </div>
      </header>

      <div className="space-y-3">
        <Card className="shadow-sm">
          <CardContent className="gap-3 p-4">
            <Badge className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100">Live</Badge>
            <CardTitle className="text-xl">Moonfall</CardTitle>
            <p className="text-sm leading-6 text-slate-600">Next session: Friday, 8:30 PM</p>
          </CardContent>
        </Card>
        <Card className="shadow-sm">
          <CardContent className="gap-3 p-4">
            <Badge className="bg-amber-100 text-amber-700 hover:bg-amber-100">Paused</Badge>
            <CardTitle className="text-xl">Whispering Vault</CardTitle>
            <p className="text-sm leading-6 text-slate-600">3 clues left before the finale</p>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

export default CampaignsSection
