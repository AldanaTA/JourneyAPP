import { BookOpenText, BriefcaseBusiness, Layers3, ScrollText, Sparkles, Swords, Dna } from "lucide-react";

export type CompendiumCategory = "Races" | "Powers" | "Traits" | "Jobs" | "Items" | "Creatures" | "Rules";

interface CategorySelectorProps {
  value: CompendiumCategory;
  onChange: (category: CompendiumCategory) => void;
}

const categories: Array<{ label: CompendiumCategory; icon: typeof Sparkles }> = [
  { label: "Rules", icon: ScrollText },
  { label: "Races", icon: Dna },
  { label: "Powers", icon: Sparkles },
  { label: "Traits", icon: Layers3 },
  { label: "Jobs", icon: BriefcaseBusiness },
  { label: "Items", icon: BookOpenText },
  { label: "Creatures", icon: Swords },
];

export function CategorySelector({ value, onChange }: CategorySelectorProps) {
  return (
    <div className="overflow-x-auto pb-1">
      <div className="flex min-w-max gap-2 rounded-xl bg-slate-100 p-1.5 md:justify-center">
        {categories.map(({ label, icon: Icon }) => {
          const isActive = value === label;

          return (
            <button
              key={label}
              type="button"
              onClick={() => onChange(label)}
              className={[
                "inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition",
                isActive
                  ? "bg-slate-900 text-white shadow-sm"
                  : "text-slate-600 hover:bg-slate-200/80 hover:text-slate-900",
              ].join(" ")}
            >
              <Icon size={16} />
              {label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

export default CategorySelector;
