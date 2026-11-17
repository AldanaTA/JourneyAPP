import { useEffect, useState } from "react";
import { Spinner } from "@/components/ui/spinner";
import { getCreatureData } from "@/apis/creatureApi";

export function CreatureLoader() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function loadCreatures() {
      try {
        const items = await getCreatureData();

        if (isMounted) {
          setError(items.length === 0 ? "Creature API not available yet." : null);
        }
      } catch (err) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : "Unable to load creatures");
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    void loadCreatures();

    return () => {
      isMounted = false;
    };
  }, []);

  if (loading) {
    return (
      <div className="flex min-h-[180px] items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-slate-600">
        <div className="flex items-center gap-2">
          <Spinner className="h-5 w-5" />
          <span>Loading creatures...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-6 text-sm text-slate-500">{error}</div>;
  }

  return <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-6 text-sm text-slate-500">Creature loader ready.</div>;
}

export default CreatureLoader;
