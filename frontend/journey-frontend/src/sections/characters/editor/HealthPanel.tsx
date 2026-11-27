import { useState } from "react";
import type { LucideIcon } from "lucide-react";
import { Brain, Droplets, Heart, HeartCrack, Minus, Shield, Zap } from "lucide-react";
import { cn } from "@/lib/utils";
import { useDraft, useStats, useUpdate } from "./hooks";
import { usePlay } from "./playContext";
import type { CapacityName } from "./rules";
import { currentOf, lowerCapacity, maxOf, raiseCapacity, takeDamage } from "./rules";

interface Row {
  name: CapacityName;
  label: string;
  lower: { label: string; icon: LucideIcon; done: string };
  raise: { label: string; icon: LucideIcon; done: string };
  bar: string;
}

const ROWS: Row[] = [
  { name: "HP", label: "Health", lower: { label: "Damage", icon: HeartCrack, done: "Took {n} damage" }, raise: { label: "Heal", icon: Heart, done: "Healed {n} HP" }, bar: "bg-emerald-500" },
  { name: "WP", label: "Willpower", lower: { label: "Damage", icon: Minus, done: "Took {n} WP damage" }, raise: { label: "Heal", icon: Brain, done: "Healed {n} WP" }, bar: "bg-indigo-500" },
  { name: "EP", label: "Energy", lower: { label: "Spend", icon: Minus, done: "Spent {n} EP" }, raise: { label: "Restore", icon: Zap, done: "Restored {n} EP" }, bar: "bg-amber-500" },
  { name: "MP", label: "Magic", lower: { label: "Spend", icon: Minus, done: "Spent {n} MP" }, raise: { label: "Restore", icon: Droplets, done: "Restored {n} MP" }, bar: "bg-sky-500" },
];

/**
 * In play: every capacity at a glance, each with its own amount box. HP: Damage (temporary HP takes it first),
 * Heal, and Temp HP (replaces it; temporary HP does not stack). WP: Damage and Heal. EP and MP: Spend and Restore.
 * Values stay between 0 and the maximum.
 */
export function HealthPanel() {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="mb-1 text-[11px] font-bold uppercase tracking-[0.18em] text-violet-700">Capacities</div>
      <div className="divide-y divide-slate-100">
        {ROWS.map((row) => <CapacityRow key={row.name} row={row} />)}
      </div>
    </section>
  );
}

function CapacityRow({ row }: { row: Row }) {
  const d = useDraft();
  const s = useStats();
  const update = useUpdate();
  const play = usePlay();
  const [amount, setAmount] = useState("");
  const current = currentOf(d, s, row.name);
  const max = maxOf(s, row.name);
  const temp = row.name === "HP" ? d.capacities.tempHp : 0;
  const n = Math.max(0, Number.parseInt(amount, 10) || 0);
  const percent = max > 0 ? Math.max(0, Math.min(100, (current / max) * 100)) : 0;

  const apply = (what: "lower" | "raise" | "temp") => {
    if (!n) return;
    update((x) => {
      if (what === "temp") x.capacities.tempHp = n;
      else if (what === "lower") {
        if (row.name === "HP") takeDamage(x, s, n);
        else lowerCapacity(x, s, row.name, n);
      } else raiseCapacity(x, s, row.name, n);
    });
    play?.notify(what === "temp" ? `Temporary HP set to ${n}` : row[what].done.replace("{n}", String(n)));
    setAmount("");
  };

  const button = "flex flex-1 items-center justify-center gap-1 rounded-lg py-2 text-xs font-semibold disabled:opacity-40";
  return (
    <div className="space-y-1.5 py-2.5">
      <div className="flex items-center gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex items-baseline gap-2">
            <span className="w-8 text-sm font-bold text-slate-900">{row.name}</span>
            <span className="text-xl font-bold tabular-nums text-slate-900">
              {current}
              <span className="text-sm font-medium text-slate-400"> / {max}</span>
            </span>
            {temp > 0 && <span className="rounded-md bg-sky-100 px-1.5 py-0.5 text-xs font-semibold text-sky-800">+{temp} temp</span>}
            <span className="ml-auto hidden text-xs text-slate-400 sm:inline">{row.label}</span>
          </div>
          <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100" aria-hidden="true">
            <div className={cn("h-full rounded-full transition-all", row.name === "HP" && percent <= 25 ? "bg-red-500" : row.bar)} style={{ width: `${percent}%` }} />
          </div>
        </div>
        <input
          value={amount}
          onChange={(e) => setAmount(e.target.value.replace(/\D/g, ""))}
          onKeyDown={(e) => e.key === "Enter" && apply("lower")}
          inputMode="numeric"
          placeholder="0"
          aria-label={`${row.name} amount`}
          className="w-16 shrink-0 rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-center text-base font-semibold tabular-nums outline-none focus:border-violet-400"
        />
      </div>
      <div className="flex gap-1.5">
        <button type="button" disabled={!n || (current <= 0 && temp <= 0)} onClick={() => apply("lower")} className={cn(button, "bg-red-50 text-red-700 hover:bg-red-100")}>
          <row.lower.icon size={14} /> {row.lower.label}
        </button>
        <button type="button" disabled={!n || current >= max} onClick={() => apply("raise")} className={cn(button, "bg-emerald-50 text-emerald-700 hover:bg-emerald-100")}>
          <row.raise.icon size={14} /> {row.raise.label}
        </button>
        {row.name === "HP" && (
          <button type="button" disabled={!n} onClick={() => apply("temp")} className={cn(button, "bg-sky-50 text-sky-700 hover:bg-sky-100")}>
            <Shield size={14} /> Temp HP
          </button>
        )}
      </div>
    </div>
  );
}
