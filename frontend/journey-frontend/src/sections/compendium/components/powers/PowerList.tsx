import type { Power } from "../../../../models/Power";
import { PowerCard } from "./PowerCard";

interface PowerListProps {
  powers: Power[];
}

export function PowerList({ powers }: PowerListProps) {
  if (!powers || powers.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-slate-700 bg-slate-900/40 p-8 text-center text-slate-400">
        No powers available.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {powers.map((power) => (
        <PowerCard key={power.id} power={power} />
      ))}
    </div>
  );
}

export default PowerList;
