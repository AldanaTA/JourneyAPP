import type { JobCategory, JobPage } from "../models/Job";

/** Body sent to POST /jobs/filter-by-category; null values are ignored by the backend. */
export interface JobFilterRequest {
  categories: JobCategory[];
  matchAll: boolean;
  /** When true, only each job's primary category is compared against `categories`. */
  primaryOnly: boolean;
  page: number;
  size: number;
  name: string | null;
  /** Matches jobs containing a trait whose name includes this text. */
  traitName: string | null;
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "/api/v1").replace(/\/$/, "");

/** Performs a job API request and verifies the response is a page of jobs. */
async function requestJobs(path: string, init?: RequestInit): Promise<JobPage> {
  const response = await fetch(`${API_BASE_URL}${path}`, init);
  if (!response.ok) {
    throw new Error(`Job API request failed: ${response.status} ${response.statusText}`);
  }

  const result: unknown = await response.json();
  if (result !== null && typeof result === "object" && "content" in result && Array.isArray(result.content)) {
    return result as JobPage;
  }

  throw new Error("Job API returned an unexpected response");
}

/** Fetches one page of all jobs, sorted by name. */
export function getJobs(page = 0, size = 15): Promise<JobPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return requestJobs(`/jobs?${params.toString()}`);
}

/** Fetches one page of jobs matching the given filters. */
export function filterJobs(filters: JobFilterRequest, page = 0, size = 15): Promise<JobPage> {
  return requestJobs("/jobs/filter-by-category", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ ...filters, page, size }),
  });
}
