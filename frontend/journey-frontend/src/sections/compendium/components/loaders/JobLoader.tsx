import { useEffect, useState } from "react";
import { Spinner } from "@/components/ui/spinner";
import { getJobData } from "@/apis/jobApi";

export function JobLoader() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function loadJobs() {
      try {
        const items = await getJobData();

        if (isMounted) {
          setError(items.length === 0 ? "Job API not available yet." : null);
        }
      } catch (err) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : "Unable to load jobs");
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    void loadJobs();

    return () => {
      isMounted = false;
    };
  }, []);

  if (loading) {
    return (
      <div className="flex min-h-[180px] items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-slate-600">
        <div className="flex items-center gap-2">
          <Spinner className="h-5 w-5" />
          <span>Loading jobs...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-6 text-sm text-slate-500">{error}</div>;
  }

  return <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-6 text-sm text-slate-500">Job loader ready.</div>;
}

export default JobLoader;
