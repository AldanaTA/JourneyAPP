import { lazy, Suspense, useState } from "react";
import { Info } from "lucide-react";
import type { Trait } from "@/models/Trait";

const TraitDetailsModal = lazy(() =>
  import("@/sections/compendium/components/traits/TraitDetailsModal").then((m) => ({ default: m.TraitDetailsModal })),
);

/** A small (i) that opens the trait's full compendium card, for lists where tapping the row picks the trait. */
export function TraitInfoButton({ trait }: { trait: Trait }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label={`Details of ${trait.traitName}`}
        title="Full details"
        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-violet-700"
      >
        <Info size={16} />
      </button>
      {open && (
        <Suspense fallback={null}>
          <TraitDetailsModal trait={trait} isOpen onClose={() => setOpen(false)} />
        </Suspense>
      )}
    </>
  );
}
