import { lazy, Suspense, useState } from "react"
import type { LucideIcon } from "lucide-react"
import {
  BookOpenText,
  Library,
  LogOut,
  Map,
  Menu,
  Settings,
  SlidersHorizontal,
  Users,
} from "lucide-react"
import { Navigate, NavLink, Route, Routes } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { Spinner } from "@/components/ui/spinner"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"

// Every page is downloaded only when it is first opened, so the initial bundle holds just the app shell.
const GameLibrarySection = lazy(() => import("./sections/game-library/GameLibrarySection"))
const CompendiumSection = lazy(() => import("./sections/compendium/CompendiumSection"))
const CharactersSection = lazy(() => import("./sections/characters/CharactersSection"))
const CampaignsSection = lazy(() => import("./sections/campaigns/CampaignsSection"))
const RuleBookReader = lazy(() => import("./sections/game-library/rules/RuleBookReader"))

/** Shown while a lazily loaded page is downloading. */
function PageLoading() {
  return (
    <div className="flex min-h-[180px] items-center justify-center gap-2 text-slate-600">
      <Spinner className="h-5 w-5" /> <span>Loading…</span>
    </div>
  )
}

type SectionDefinition = {
  id: string
  label: string
  path: string
  icon: LucideIcon
}

const sections: SectionDefinition[] = [
  { id: "library", label: "Library", path: "/library", icon: Library },
  { id: "compendium", label: "Compendium", path: "/compendium", icon: BookOpenText },
  { id: "characters", label: "Characters", path: "/characters", icon: Users },
  { id: "campaigns", label: "Campaigns", path: "/campaigns", icon: Map },
]

const menuOptions = [
  { label: "Account Settings", icon: Settings, description: "Profile & permissions" },
  { label: "UI Settings", icon: SlidersHorizontal, description: "Theme & preferences" },
  { label: "Exit", icon: LogOut, description: "Log out of Journey" },
]

function App() {
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <div className="min-h-screen bg-[#f5efe7] text-slate-900">

      {/*Mobile View */}
      <div className="lg:hidden">
        <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
          <SheetContent
            side="left"
            showCloseButton
            className="w-[86%] max-w-[320px] border-r border-slate-200 bg-[#fffdfb] p-5"
          >
            <SheetHeader className="mb-6 items-start gap-3 border-b border-slate-200 pb-4">
              <div className="flex items-center gap-3 rounded-2xl bg-slate-50 p-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-full bg-gradient-to-br from-violet-500 to-indigo-500 text-sm font-black text-white">
                  J
                </div>
                <div>
                  <SheetTitle className="text-left text-base font-semibold text-slate-900">
                    Jay Carter
                  </SheetTitle>
                  <SheetDescription className="text-left text-sm text-slate-500">
                    Game Keeper
                  </SheetDescription>
                </div>
              </div>
            </SheetHeader>

            <nav className="space-y-2">
              {menuOptions.map(({ label, icon: Icon, description }) => (
                <Button
                  key={label}
                  type="button"
                  variant="ghost"
                  className="flex w-full justify-start gap-3 rounded-xl p-3 text-left hover:bg-slate-100"
                >
                  <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-violet-100 text-violet-700">
                    <Icon size={18} />
                  </span>
                  <span className="flex-1">
                    <span className="block text-sm font-semibold text-slate-900">{label}</span>
                    <span className="block text-xs text-slate-500">{description}</span>
                  </span>
                </Button>
              ))}
            </nav>
          </SheetContent>
        </Sheet>

        <div className="relative mx-auto flex min-h-screen max-w-[480px] flex-col bg-white/40 shadow-[0_20px_50px_rgba(25,30,36,0.12)] backdrop-blur-xl">
          <header className="sticky top-0 z-10 flex items-center gap-3 border-b border-white/10 bg-[#201a12]/95 px-3 py-3 backdrop-blur-md">
            <Button
              type="button"
              variant="outline"
              size="icon"
              aria-label="Open menu"
              onClick={() => setMenuOpen(true)}
              className="h-9 w-9 rounded-xl border-white/10 bg-white/5 text-white hover:bg-white/10"
            >
              <Menu size={20} />
            </Button>

            <nav
              className="flex flex-1 items-center justify-center gap-1.5 overflow-x-auto rounded-2xl border border-white/10 bg-white/5 p-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
              aria-label="Choose app section"
            >
              {sections.map(({ id, label, path, icon: Icon }) => (
                <NavLink
                  key={id}
                  to={path}
                  className={({ isActive }) =>
                    `flex items-center justify-center gap-1.5 rounded-xl px-2.5 py-2 text-[10px] font-semibold tracking-wide transition-colors ${
                      isActive
                        ? "bg-gradient-to-r from-violet-500 to-violet-400 text-white shadow-lg shadow-violet-500/30"
                        : "text-white/80 hover:bg-white/10 hover:text-white"
                    }`
                  }
                  aria-label={label}
                  title={label}
                >
                  <span className="flex items-center justify-center gap-1.5">
                    <Icon size={14} />
                    <span>{label.substring(0, 4)}</span>
                  </span>
                </NavLink>
              ))}
            </nav>
          </header>

          <main className="flex-1 px-4 py-5">
            <Suspense fallback={<PageLoading />}>
              <Routes>
                <Route path="/" element={<Navigate to="/library" replace />} />
                <Route path="/library" element={<GameLibrarySection />} />
                <Route path="/library/books/:bookId" element={<RuleBookReader />} />
                <Route path="/library/books/:bookId/chapters/:chapterId" element={<RuleBookReader />} />
                <Route path="/compendium" element={<CompendiumSection />} />
                <Route path="/characters" element={<CharactersSection />} />
                <Route path="/campaigns" element={<CampaignsSection />} />
              </Routes>
            </Suspense>
          </main>
        </div>
      </div>
      
      {/*Desktop View */}
      <div className="hidden min-h-screen bg-[#f5efe7] lg:flex lg:items-stretch lg:justify-center lg:p-8">
        <div className="relative mx-auto flex min-h-[calc(100vh-4rem)] w-full max-w-[1400px] flex-col overflow-clip rounded-[32px] border border-slate-200 bg-white/90 shadow-[0_30px_80px_rgba(15,23,42,0.12)]">
          <header className="flex items-center gap-3 border-b border-white/10 bg-[#201a12]/95 px-3 py-3 backdrop-blur-md">
           

            <Button
              type="button"
              variant="outline"
              size="icon"
              aria-label="Open menu"
              onClick={() => setMenuOpen(true)}
              className="h-9 w-9 rounded-xl border-white/10 bg-white/5 text-white hover:bg-white/10"
            >
              <Menu size={20} />
            </Button>

            <nav
              className="flex flex-1 items-center justify-around gap-1.5 overflow-x-auto rounded-2xl border border-white/10 bg-white/5 p-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
              aria-label="Choose app section"
            >
              {sections.map(({ id, label, path, icon: Icon }) => (
                <NavLink
                  key={id}
                  to={path}
                  className={({ isActive }) =>
                    `flex items-center justify-center gap-1.5 rounded-xl px-3 py-2 text-[11px] font-semibold tracking-wide transition-colors ${
                      isActive
                        ? "bg-gradient-to-r from-violet-500 to-violet-400 text-white shadow-lg shadow-violet-500/30"
                        : "text-white/80 hover:bg-white/10 hover:text-white"
                    }`
                  }
                  aria-label={label}
                  title={label}
                >
                  <Icon size={14} />
                  <span>{label}</span>
                </NavLink>
              ))}
            </nav>
          </header>

          <main className="flex-1 bg-[#f5efe7] px-4 py-5 lg:px-6 lg:py-6">
            <div className="mx-auto max-w-[1200px]">
              <Suspense fallback={<PageLoading />}>
                <Routes>
                  <Route path="/" element={<Navigate to="/library" replace />} />
                  <Route path="/library" element={<GameLibrarySection />} />
                  <Route path="/library/books/:bookId" element={<RuleBookReader />} />
                  <Route path="/library/books/:bookId/chapters/:chapterId" element={<RuleBookReader />} />
                  <Route path="/compendium" element={<CompendiumSection />} />
                  <Route path="/characters" element={<CharactersSection />} />
                  <Route path="/campaigns" element={<CampaignsSection />} />
                </Routes>
              </Suspense>
            </div>
          </main>
        </div>
      </div>
    </div>
  )
}

export default App
