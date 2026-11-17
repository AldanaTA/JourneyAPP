import { useEffect, useState } from "react";
import type { Power } from "../../../../models/Power";
import { Spinner } from "@/components/ui/spinner";
import { PowerList } from "../powers/PowerList";
import { getDemoPowers } from "../../../../apis/powerApi";

export function PowerLoader() {
  const [powers, setPowers] = useState<Power[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function loadPowers() {
      try {
        const items = await getDemoPowers();

        if (isMounted) {
          setPowers(items);
          setError(null);
        }
      } catch (err) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : "Unable to load powers");
          setPowers([]);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    void loadPowers();

    return () => {
      isMounted = false;
    };
  }, []);

  if (loading) {
    return (
      <div className="flex min-h-[180px] items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-slate-600">
        <div className="flex items-center gap-2">
          <Spinner className="h-5 w-5" />
          <span>Loading powers...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        Unable to load powers: {error}
      </div>
    );
  }

  return <PowerList powers={powers} />;
}

export default PowerLoader;
