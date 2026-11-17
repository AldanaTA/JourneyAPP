import { EllipsisVertical, Search, X } from "lucide-react";
import { useState } from "react";
import type { PowerCategory } from "@/models/Power";

const powerCategoryOptions: PowerCategory[] = [
    "ACID"
    ,"BLEEDING"
    ,"BLESSED"
    ,"BUFF"
    ,"BLINDED"
    ,"BLUDGEONING"
    ,"CHARMED"
    ,"COLD"
    ,"CONTROL"
    ,"CURSED"
    ,"DAMAGE"
    ,"DARK"
    ,"DAZED"
    ,"DEAFENED"
    ,"DEBUFF"
    ,"DECLARED"
    ,"DEFENSE"
    ,"ENFEEBLE"
    ,"EXHAUSTION"
    ,"FIRE"
    ,"FORCE"
    ,"FRIGHTENED"
    ,"HEALING_RECOVERY"
    ,"IMPAIRED"
    ,"INCAPACITATED"
    ,"INFATUATED"
    ,"INSPIRED"
    ,"INVISIBLE"
    ,"LIGHT"
    ,"LIGHTNING"
    ,"LIGHT_ATTACK"
    ,"MAGIC"
    ,"MIGHTY"
    ,"MOBILITY"
    ,"PETRIFIED"
    ,"PIERCING"
    ,"POISON"
    ,"PRONE"
    ,"PSYCHIC"
    ,"RESTRAINED"
    ,"RUSH"
    ,"SILENCED"
    ,"SLASHING"
    ,"SLUGGISH"
    ,"STUNNED"
    ,"SUPPRESSED"
    ,"STRONG_ATTACK"
    ,"SUMMONING_COMPANIONS"
    ,"TRIGGERED"
    ,"UNCONSCIOUS"
    ,"VULNERABLE"
];

type Operator = "lte" | "lt" | "eq" | "gt" | "gte";

interface PowerFilterState {
  search: string;
  categories: PowerCategory[];
  matchAll: boolean;
  tpCost: number | "";
  tpOperator: Operator;
  hpCost: number | "";
  hpOperator: Operator;
  mpCost: number | "";
  mpOperator: Operator;
  epCost: number | "";
  epOperator: Operator;
}

const initialState: PowerFilterState = {
  search: "",
  categories: [],
  matchAll: false,
  tpCost: "",
  tpOperator: "lte",
  hpCost: "",
  hpOperator: "lte",
  mpCost: "",
  mpOperator: "lte",
  epCost: "",
  epOperator: "lte",
};

export function PowerFilter() {
  const [search, setSearch] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);
  const [filters, setFilters] = useState<PowerFilterState>(initialState);

  const toggleCategory = (category: PowerCategory) => {
    setFilters((current) => ({
      ...current,
      categories: current.categories.includes(category)
        ? current.categories.filter((item) => item !== category)
        : [...current.categories, category],
    }));
  };

  const updateCostField = (field: "tpCost" | "hpCost" | "mpCost" | "epCost", value: string) => {
    setFilters((current) => ({
      ...current,
      [field]: value === "" ? "" : Number(value),
    }));
  };

  const updateOperator = (field: "tpOperator" | "hpOperator" | "mpOperator" | "epOperator", value: Operator) => {
    setFilters((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const clearFilters = () => {
    setFilters(initialState);
    setSearch("");
  };

  const applyFilters = () => {
    setMenuOpen(false);
    // placeholder: this is where the API request payload would be created later
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white p-2 shadow-sm">
        <div className="flex flex-1 items-center gap-2 rounded-lg bg-slate-100 px-3 py-2">
          <Search className="h-4 w-4 text-slate-500" />
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search powers..."
            className="w-full border-0 bg-transparent text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none"
          />
        </div>

        <div className="relative">
          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 bg-slate-100 text-slate-600 transition hover:bg-slate-200"
            aria-label="Open power filter options"
          >
            <EllipsisVertical className="h-4 w-4" />
          </button>
        </div>
      </div>

      {menuOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4" onClick={() => setMenuOpen(false)}>
          <div
            className="w-full max-w-xl rounded-2xl border border-slate-200 bg-white p-4 shadow-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="mb-4 flex items-center justify-between gap-3">
              <h3 className="text-lg font-semibold text-slate-900">Power Filters</h3>
              <button
                type="button"
                onClick={() => setMenuOpen(false)}
                className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-600 hover:bg-slate-100"
                aria-label="Close filter modal"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="space-y-5">
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">Categories</label>
                <div className="flex max-h-40 flex-wrap gap-2 overflow-y-auto rounded-xl border border-slate-200 bg-slate-50 p-2">
                  {powerCategoryOptions.map((category) => {
                    const selected = filters.categories.includes(category);
                    return (
                      <button
                        key={category}
                        type="button"
                        onClick={() => toggleCategory(category)}
                        className={[
                          "rounded-full border px-2.5 py-1 text-[10px] font-medium uppercase tracking-wide transition",
                          selected
                            ? "border-violet-500 bg-violet-100 text-violet-700"
                            : "border-slate-200 bg-white text-slate-600 hover:bg-slate-100",
                        ].join(" ")}
                      >
                        {category}
                      </button>
                    );
                  })}
                </div>

                <label className="mt-3 flex items-center gap-2 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    checked={filters.matchAll}
                    onChange={(event) =>
                      setFilters((current) => ({
                        ...current,
                        matchAll: event.target.checked,
                      }))
                    }
                  />
                  Match all selected categories
                </label>
              </div>

              <div className="space-y-3">
                <label className="block text-sm font-medium text-slate-700">Resource Costs</label>

                {[
                  { label: "TP", key: "tpCost" as const, opKey: "tpOperator" as const },
                  { label: "HP", key: "hpCost" as const, opKey: "hpOperator" as const },
                  { label: "MP", key: "mpCost" as const, opKey: "mpOperator" as const },
                  { label: "EP", key: "epCost" as const, opKey: "epOperator" as const },
                ].map(({ label, key, opKey }) => (
                  <div key={label} className="grid grid-cols-[70px_120px_minmax(0,1fr)] items-center gap-2">
                    <span className="text-sm text-slate-700">{label}</span>

                    <select
                      value={filters[opKey]}
                      onChange={(event) => updateOperator(opKey, event.target.value as Operator)}
                      className="rounded-lg border border-slate-200 bg-slate-50 px-2 py-2 text-sm text-slate-700 outline-none"
                    >
                      <option value="lte">≤</option>
                      <option value="lt">&lt;</option>
                      <option value="eq">=</option>
                      <option value="gt">&gt;</option>
                      <option value="gte">≥</option>
                    </select>

                    <input
                      type="number"
                      min="0"
                      value={filters[key]}
                      onChange={(event) => updateCostField(key, event.target.value)}
                      placeholder="0"
                      className="rounded-lg border border-slate-200 bg-slate-50 px-2 py-2 text-sm text-slate-700 outline-none"
                    />
                  </div>
                ))}
              </div>
            </div>

            <div className="mt-5 flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={clearFilters}
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 hover:bg-slate-100"
              >
                Clear
              </button>
              <button
                type="button"
                onClick={applyFilters}
                className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-700"
              >
                Apply
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default PowerFilter;
