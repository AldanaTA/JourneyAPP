import { lazy, Suspense, useState } from "react"
import { DESKTOP_QUERY, useMediaQuery } from "@/hooks/use-media-query"
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
import { Navigate, NavLink, Route, Routes, useLocation, useNavigate } from "react-router-dom"
import { signOut } from "@/auth/authApi"
import { useAuthStore } from "@/auth/authStore"
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
const CharacterEditor = lazy(() => import("./sections/characters/editor/CharacterEditor"))
const CampaignsSection = lazy(() => import("./sections/campaigns/CampaignsSection"))
const RuleBookReader = lazy(() => import("./sections/game-library/rules/RuleBookReader"))
const SignInPage = lazy(() => import("./sections/auth/SignInPage"))
const ForgotPasswordPage = lazy(() => import("./sections/auth/ForgotPasswordPage"))
const ResetPasswordPage = lazy(() => import("./sections/auth/ResetPasswordPage"))
const AccountPage = lazy(() => import("./sections/auth/AccountPage"))

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

type MenuOption = {
  label: string
  icon: LucideIcon
  description: string
  onSelect?: () => void
}

/** The app for signed-in users, shared by the mobile and desktop layouts. */
const appRoutes = (
  <Routes>
    <Route path="/" element={<Navigate to="/library" replace />} />
    {/* Just signed in: the sign-in page sends the user on to where they were going. */}
    <Route path="/login" element={<SignInPage />} />
    {/* A reset link may be opened while signed in; resetting signs this browser out too. */}
    <Route path="/reset-password" element={<ResetPasswordPage />} />
    <Route path="/account" element={<AccountPage />} />
    <Route path="/library" element={<GameLibrarySection />} />
    <Route path="/library/books/:bookId" element={<RuleBookReader />} />
    <Route path="/library/books/:bookId/chapters/:chapterId" element={<RuleBookReader />} />
    <Route path="/compendium" element={<CompendiumSection />} />
    <Route path="/characters" element={<CharactersSection />} />
    {/* Creating (/characters/new/edit), editing, and viewing a character share one full-screen editor. */}
    <Route path="/characters/:characterId/edit" element={<CharacterEditor mode="edit" />} />
    <Route path="/characters/:characterId" element={<CharacterEditor mode="view" />} />
    <Route path="/campaigns" element={<CampaignsSection />} />
    <Route path="*" element={<Navigate to="/library" replace />} />
  </Routes>
)

/** Sends a signed-out visitor to the sign-in page, remembering where they were going. */
function RedirectToSignIn() {
  const location = useLocation()
  const from = location.pathname + location.search + location.hash
  return <Navigate to="/login" replace state={from === "/" ? undefined : { from }} />
}

function App() {
  const [menuOpen, setMenuOpen] = useState(false)
  const isDesktop = useMediaQuery(DESKTOP_QUERY)
  const status = useAuthStore((state) => state.status)
  const user = useAuthStore((state) => state.user)
  const navigate = useNavigate()

  const closeMenuAnd = (action: () => void) => () => {
    setMenuOpen(false)
    action()
  }

  // Until the session is known, show nothing of the app; signed-out visitors only get the sign-in page.
  if (status === "loading") {
    return (
      <div className="flex min-h-screen items-center justify-center gap-2 bg-[#f5efe7] text-slate-600">
        <Spinner className="h-5 w-5" /> <span>Checking your session…</span>
      </div>
    )
  }
  if (!user) {
    return (
      <main className="flex min-h-screen items-start justify-center bg-[#f5efe7] px-4 py-10 text-slate-900 sm:items-center">
        <div className="w-full">
          <Suspense fallback={<PageLoading />}>
            <Routes>
              <Route path="/login" element={<SignInPage />} />
              <Route path="/forgot-password" element={<ForgotPasswordPage />} />
              <Route path="/reset-password" element={<ResetPasswordPage />} />
              <Route path="*" element={<RedirectToSignIn />} />
            </Routes>
          </Suspense>
        </div>
      </main>
    )
  }

  const menuOptions: MenuOption[] = [
    {
      label: "Account Settings",
      icon: Settings,
      description: "Profile & password",
      onSelect: closeMenuAnd(() => navigate("/account")),
    },
    { label: "UI Settings", icon: SlidersHorizontal, description: "Theme & preferences" },
    {
      label: "Sign out",
      icon: LogOut,
      description: "Sign out of Journey",
      onSelect: closeMenuAnd(() => void signOut().then(() => navigate("/login", { replace: true }))),
    },
  ]

  const userName = user.displayName
  const userRole = user.admin ? "Admin" : user.username ?? "Player"

  return (
    <div className="min-h-screen bg-[#f5efe7] text-slate-900">

      {/* One menu sheet, opened from either layout. */}
      <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
        <SheetContent
          side="left"
          showCloseButton
          className="w-[86%] max-w-[320px] border-r border-slate-200 bg-[#fffdfb] p-5"
        >
          <SheetHeader className="mb-6 items-start gap-3 border-b border-slate-200 pb-4">
            <div className="flex items-center gap-3 rounded-2xl bg-slate-50 p-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-full bg-gradient-to-br from-violet-500 to-indigo-500 text-sm font-black uppercase text-white">
                {userName.charAt(0)}
              </div>
              <div>
                <SheetTitle className="text-left text-base font-semibold text-slate-900">
                  {userName}
                </SheetTitle>
                <SheetDescription className="text-left text-sm text-slate-500">
                  {userRole}
                </SheetDescription>
              </div>
            </div>
          </SheetHeader>

          <nav className="space-y-2">
            {menuOptions.map(({ label, icon: Icon, description, onSelect }) => (
              <Button
                key={label}
                type="button"
                variant="ghost"
                onClick={onSelect}
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

      {/* Only the layout for the current screen size is mounted, so pages render and fetch once. */}
      {isDesktop ? (
        <div className="flex min-h-screen items-stretch justify-center bg-[#f5efe7] p-8">
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

            <main className="flex-1 bg-[#f5efe7] px-6 py-6">
              <div className="mx-auto max-w-[1200px]">
                <Suspense fallback={<PageLoading />}>
                  {appRoutes}
                </Suspense>
              </div>
            </main>
          </div>
        </div>
      ) : (
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
              {appRoutes}
            </Suspense>
          </main>
        </div>
      )}
    </div>
  )
}

export default App
