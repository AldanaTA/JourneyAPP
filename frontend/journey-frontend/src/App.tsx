import { useState } from "react"
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
import "./App.css"
import GameLibrarySection from "./sections/game-library/GameLibrarySection"
import CompendiumSection from "./sections/compendium/CompendiumSection"
import CharactersSection from "./sections/characters/CharactersSection"
import CampaignsSection from "./sections/campaigns/CampaignsSection"

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
    <div className="journey-app">
      <div
        className={`side-menu-backdrop ${menuOpen ? "is-open" : ""}`}
        onClick={() => setMenuOpen(false)}
      />

      <aside className={`side-menu ${menuOpen ? "is-open" : ""}`} aria-label="Account menu">
        <div className="side-menu-header">
          <div className="avatar profile">J</div>
          <div>
            <p className="profile-name">Jay Carter</p>
            <span className="profile-role">Game Keeper</span>
          </div>
        </div>

        <nav className="side-menu-nav">
          {menuOptions.map(({ label, icon: Icon, description }) => (
            <button key={label} className="menu-item" type="button">
              <span className="menu-icon">
                <Icon size={18} />
              </span>
              <span>
                <strong>{label}</strong>
                <small>{description}</small>
              </span>
            </button>
          ))}
        </nav>
      </aside>

      <div className="mobile-shell">
        <header className="top-header">
          <button
            className="menu-button"
            type="button"
            aria-label="Open menu"
            onClick={() => setMenuOpen(true)}
          >
            <Menu size={22} />
          </button>

          <nav className="section-selector" aria-label="Choose app section">
            {sections.map(({ id, label, path, icon: Icon }) => (
              <NavLink
                key={id}
                to={path}
                end={path === "/library"}
                className={({ isActive }) => `section-tab ${isActive ? "selected" : ""}`}
              >
                <Icon size={18} />
                <span>{label}</span>
              </NavLink>
            ))}
          </nav>
        </header>

        <main className="app-content">
          <Routes>
            <Route path="/" element={<Navigate to="/library" replace />} />
            <Route path="/library" element={<GameLibrarySection />} />
            <Route path="/compendium" element={<CompendiumSection />} />
            <Route path="/characters" element={<CharactersSection />} />
            <Route path="/campaigns" element={<CampaignsSection />} />
          </Routes>
        </main>
      </div>
    </div>
  )
}

export default App
