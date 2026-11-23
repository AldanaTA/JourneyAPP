import { EllipsisVertical, Search } from "lucide-react";
import { useState } from "react";

export function CreatureFilter() {
  const [search, setSearch] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);

  const filterOptions = ["All", "Type", "Threat", "Name"];

  return (
    <div className="flex items-center gap-2">
      <div className="flex flex-1 items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 shadow-sm">
        <Search className="h-4 w-4 text-slate-500" />
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search creatures..."
          className="w-full border-0 bg-transparent text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none"
        />
      </div>

      <div className="relative">
        <button
          type="button"
          onClick={() => setMenuOpen((open) => !open)}
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600 shadow-sm transition hover:bg-slate-100"
          aria-label="Creature filter options"
        >
          <EllipsisVertical className="h-4 w-4" />
        </button>

        {menuOpen && (
          <div className="absolute right-0 top-11 z-10 w-40 rounded-lg border border-slate-200 bg-white p-1 shadow-lg">
            {filterOptions.map((option) => (
              <button
                key={option}
                type="button"
                className="flex w-full items-center justify-between rounded-md px-2 py-2 text-left text-sm text-slate-700 hover:bg-slate-100"
              >
                <span>{option}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default CreatureFilter;
