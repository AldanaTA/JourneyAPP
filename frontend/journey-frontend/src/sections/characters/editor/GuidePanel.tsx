import { useState } from "react";
import type { ReactNode } from "react";
import { ArrowLeft, ArrowRight, CircleAlert, CircleCheck, Compass, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { useDraftStore } from "./draftStore";
import { useBudget, useDraft, useSheet, useSheetOptions, useStats } from "./hooks";
import { MIXED_RACE_TRAIT_EXP, POWER_COST, creationProblems } from "./rules";

export type GuideTab = "identity" | "attributes" | "job" | "traits" | "powers" | "skills" | "inventory";

interface Step {
  tab: GuideTab;
  title: string;
  /** What this part is and what to do, shown above the tab. */
  explain: () => ReactNode;
  /** How far the character is with this part. */
  Status: () => ReactNode;
}

function Ok({ children }: { children: ReactNode }) {
  return <p className="flex items-start gap-1.5 text-sm font-medium text-emerald-700"><CircleCheck size={15} className="mt-0.5 shrink-0" /> {children}</p>;
}

function Todo({ children }: { children: ReactNode }) {
  return <p className="flex items-start gap-1.5 text-sm font-medium text-amber-700"><CircleAlert size={15} className="mt-0.5 shrink-0" /> {children}</p>;
}

/** The guided creation steps, in order; each opens the tab where that part is filled in. */
const STEPS: Step[] = [
  {
    tab: "identity",
    title: "Your character",
    explain: () => (
      <>
        <p>
          You build a character with two budgets. <b>150 EXP</b> buys attributes, skills, traits, and powers; the EXP left is at
          the top of the screen. <b>50 job points</b> pay for your job's traits, and what is left can raise attributes or skills.
        </p>
        <p>Start with a <b>name</b>. The rest of the story (description, motive, ideals, strengths, flaws, history) is optional, but it helps your storyteller.</p>
        <p className="text-slate-500">Nothing is sent until you press Save, which also happens on its own every 30 seconds.</p>
      </>
    ),
    Status: () => {
      const d = useDraft();
      return d.name.trim() ? <Ok>Named {d.name.trim()}</Ok> : <Todo>Give your character a name (Identity, at the top).</Todo>;
    },
  },
  {
    tab: "identity",
    title: "Character sheet",
    explain: () => <SheetExplain />,
    Status: () => {
      const sheet = useSheet();
      return sheet ? <Ok>{sheet.name}: Body {sheet.baseBody}, Strength {sheet.baseStrength}, Speed {sheet.baseSpeed}, Magic {sheet.baseMagic}</Ok>
        : <Todo>Under Origin, tap Character sheet and pick one.</Todo>;
    },
  },
  {
    tab: "identity",
    title: "Race",
    explain: () => (
      <>
        <p>Your race gives your <b>movement</b>, the <b>sizes</b> you can be, a <b>language set</b>, and <b>racial traits</b> for free.</p>
        <p>
          Pick one race to get all of its racial traits. Pick <b>more than one</b> for a mixed race: you then choose racial traits
          from all of them worth exactly <b>{MIXED_RACE_TRAIT_EXP} EXP</b>, and which race's language set you speak.
        </p>
        <p>Then pick your size from the race's range. Tap a card in the race list to read the race before choosing.</p>
      </>
    ),
    Status: () => {
      const d = useDraft();
      const b = useBudget();
      if (d.races.length === 0) return <Todo>Under Origin, tap Race.</Todo>;
      const missing = [!d.languageRaceId && "a language set", !d.size && "a size",
        d.races.length > 1 && b.racialTraitCost !== MIXED_RACE_TRAIT_EXP && `racial traits worth ${MIXED_RACE_TRAIT_EXP} EXP (now ${b.racialTraitCost})`].filter(Boolean);
      return missing.length ? <Todo>{d.races.map((r) => r.name).join(" / ")}: still choose {missing.join(", ")}.</Todo>
        : <Ok>{d.races.map((r) => r.name).join(" / ")}, {d.size}</Ok>;
    },
  },
  {
    tab: "job",
    title: "Job",
    explain: () => (
      <>
        <p>Your job is what your character does, and it offers a list of <b>job traits</b>.</p>
        <p>Job traits are bought with your <b>50 job points</b>: each costs its purchase cost in job points (not EXP). Tick the ones you want; tap (i) to read one first.</p>
      </>
    ),
    Status: () => {
      const d = useDraft();
      const picked = d.traits.filter((t) => t.source === "JOB").length;
      return d.job ? <Ok>{d.job.name}, {picked} job trait{picked === 1 ? "" : "s"}</Ok> : <Todo>Tap Select job at the top.</Todo>;
    },
  },
  {
    tab: "job",
    title: "Leftover job points",
    explain: () => (
      <p>
        Job points you do not spend on job traits can go into <b>attributes or skills</b>: 1 job point raises one by 1. Use the ±
        buttons under "Job points on attributes" and "Job points on skills". They never buy purchased traits or powers.
      </p>
    ),
    Status: () => {
      const b = useBudget();
      return b.jobPointsRemaining === 0 ? <Ok>All job points spent</Ok>
        : b.jobPointsRemaining > 0 ? <Todo>{b.jobPointsRemaining} job points left (they are lost if unspent)</Todo>
          : <Todo>Overspent by {-b.jobPointsRemaining} job points</Todo>;
    },
  },
  {
    tab: "attributes",
    title: "Attributes",
    explain: () => (
      <>
        <p><b>1 EXP raises an attribute by 1.</b> Attributes set everything else:</p>
        <ul className="list-disc space-y-0.5 pl-5">
          <li><b>Body</b>: HP (= Body) and WP (= half of Body)</li>
          <li><b>Strength</b>: EP, and +2 physical damage per 15</li>
          <li><b>Speed</b>: +1 priority and +5 ft movement per 15</li>
          <li><b>Magic</b>: MP, and +2 magic damage per 15</li>
        </ul>
        <p>Bonuses from traits or items go in the Bonus box of each ±.</p>
      </>
    ),
    Status: () => {
      const s = useStats();
      return <Ok>HP {s.maxHp} · WP {s.maxWp} · EP {s.maxEp} · MP {s.maxMp} · Priority {s.priority} · {s.movementFt} ft</Ok>;
    },
  },
  {
    tab: "skills",
    title: "Skills",
    explain: () => (
      <>
        <p>Skills are percentages: you roll a d100 and succeed at or under your rating. <b>1 EXP raises a skill by 1.</b></p>
        <p>
          While creating, you can also <b>swap</b>: lower a skill by up to 20 to raise another skill of the same kind (combat or
          life) by the same amount. Swaps in each kind must add up to 0.
        </p>
      </>
    ),
    Status: () => {
      const b = useBudget();
      return b.combatSwap === 0 && b.lifeSwap === 0 ? <Ok>Swaps balanced</Ok>
        : <Todo>Swaps must balance: combat {b.combatSwap > 0 ? "+" : ""}{b.combatSwap}, life {b.lifeSwap > 0 ? "+" : ""}{b.lifeSwap}</Todo>;
    },
  },
  {
    tab: "traits",
    title: "Traits",
    explain: () => (
      <>
        <p>Traits are special abilities bought with <b>EXP</b> (their purchase cost). Tap <b>+</b> to browse; tap a card for its full details.</p>
        <p>Raising a trait a level costs <b>half its cost</b>, up to level 6. Your racial and job traits are listed here too.</p>
      </>
    ),
    Status: () => {
      const d = useDraft();
      const b = useBudget();
      const bought = d.traits.filter((t) => t.source === "PURCHASED").length;
      return <Ok>{bought} bought · {b.expRemaining} EXP left</Ok>;
    },
  },
  {
    tab: "powers",
    title: "Powers",
    explain: () => (
      <p>
        Powers are spells and techniques: each costs <b>{POWER_COST} EXP</b> to learn, and raising one from level L costs <b>5 × L</b> EXP.
        Casting them spends MP, EP, or HP. Tap <b>+</b> to browse. Skip this if your character does not use powers.
      </p>
    ),
    Status: () => {
      const d = useDraft();
      const b = useBudget();
      return <Ok>{d.powers.length} power{d.powers.length === 1 ? "" : "s"} · {b.expRemaining} EXP left</Ok>;
    },
  },
  {
    tab: "inventory",
    title: "Gear",
    explain: () => (
      <p>
        Put on <b>armor</b>, take something in each <b>hand</b> (or a two-handed weapon), and equip a <b>bag</b>. Items you carry go in
        <b> slots</b>: add the slots your bag or traits give ("Add slots"), then store items in them. Tap an item for what it does.
      </p>
    ),
    Status: () => {
      const d = useDraft();
      return <Ok>{d.inventory.items.length} item{d.inventory.items.length === 1 ? "" : "s"} · {d.inventory.slots.length} slots</Ok>;
    },
  },
  {
    tab: "identity",
    title: "Finish",
    explain: () => (
      <p>
        Check the list below. When every creation rule is met, press <b>Finish creation</b> at the bottom of this page to make the
        character ready to play. Until then it stays a draft you can come back to.
      </p>
    ),
    Status: () => {
      const d = useDraft();
      const b = useBudget();
      const problems = creationProblems(d, b);
      return problems.length === 0 ? <Ok>Every rule is met: press Finish creation below.</Ok>
        : <div className="space-y-0.5">{problems.map((p) => <Todo key={p}>{p}</Todo>)}</div>;
    },
  },
];

function SheetExplain() {
  const { official, campaign } = useSheetOptions();
  return (
    <>
      <p>Your character sheet sets your <b>starting attributes</b>: Body, Strength, Speed, and Magic.</p>
      <ul className="list-disc space-y-0.5 pl-5">
        {[...official, ...campaign].map((s) => (
          <li key={s.id}>
            <b>{s.name}</b>: Body {s.baseBody}, Strength {s.baseStrength}, Speed {s.baseSpeed}, Magic {s.baseMagic}
            {s.description ? ` — ${s.description}` : ""}
          </li>
        ))}
      </ul>
    </>
  );
}

/**
 * Guided creation: the current step's explanation and status above the tab, with Back / Next, and a way to leave
 * the guide. {@code onStep} opens the step's tab.
 */
export function GuidePanel({ step, onStep }: { step: number; onStep: (step: number, tab: GuideTab) => void }) {
  const setGuided = useDraftStore((state) => state.setGuided);
  const [collapsed, setCollapsed] = useState(false);
  const current = STEPS[Math.min(step, STEPS.length - 1)];
  const go = (to: number) => onStep(to, STEPS[to].tab);

  return (
    <section className="mb-4 rounded-2xl border border-violet-200 bg-gradient-to-br from-violet-50 to-white p-4 shadow-sm">
      <div className="flex items-center gap-2">
        <Compass size={16} className="text-violet-600" />
        <span className="text-[11px] font-bold uppercase tracking-[0.18em] text-violet-700">
          Step {step + 1} of {STEPS.length}
        </span>
        <button type="button" onClick={() => setCollapsed(!collapsed)} className="ml-auto text-xs font-medium text-violet-700">
          {collapsed ? "Show tips" : "Hide tips"}
        </button>
        <button type="button" onClick={() => setGuided(false)} aria-label="Leave the guide" title="Leave the guide"
          className="rounded-lg p-1 text-slate-400 hover:bg-white hover:text-slate-700">
          <X size={15} />
        </button>
      </div>
      <h2 className="mt-1 text-xl font-semibold text-slate-900">{current.title}</h2>
      {/* Step dots: tap one to jump there. */}
      <div className="mt-2 flex gap-1" role="tablist" aria-label="Steps">
        {STEPS.map((s, i) => (
          <button key={s.title} type="button" role="tab" aria-selected={i === step} aria-label={s.title} title={s.title}
            onClick={() => go(i)}
            className={cn("h-1.5 flex-1 rounded-full transition", i === step ? "bg-violet-600" : i < step ? "bg-violet-300" : "bg-slate-200")} />
        ))}
      </div>
      {!collapsed && <div className="mt-3 space-y-2 text-sm leading-relaxed text-slate-700">{current.explain()}</div>}
      <div className="mt-3 rounded-xl bg-white/80 px-3 py-2 ring-1 ring-violet-100"><current.Status /></div>
      <div className="mt-3 flex items-center justify-between gap-2">
        <button type="button" disabled={step === 0} onClick={() => go(step - 1)}
          className="flex items-center gap-1 rounded-lg px-3 py-2 text-sm font-semibold text-slate-600 hover:bg-white disabled:opacity-30">
          <ArrowLeft size={15} /> Back
        </button>
        {step < STEPS.length - 1 ? (
          <button type="button" onClick={() => go(step + 1)}
            className="flex items-center gap-1 rounded-lg bg-violet-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-violet-700">
            Next: {STEPS[step + 1].title} <ArrowRight size={15} />
          </button>
        ) : (
          <button type="button" onClick={() => setGuided(false)}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800">
            Close the guide
          </button>
        )}
      </div>
    </section>
  );
}
