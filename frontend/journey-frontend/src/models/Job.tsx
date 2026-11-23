import type { Trait } from "./Trait";

/** Every category a job can be tagged with; mirrors the seeded rows of the backend job_categories table. */
export type JobCategory =
  | "Caster"
  | "Companion"
  | "Crafter"
  | "Defender"
  | "Divine"
  | "Generalist"
  | "Half-Caster"
  | "Martial"
  | "Nature"
  | "Occult"
  | "Ranged"
  | "Saboteur"
  | "Scout"
  | "Social"
  | "Specialist"
  | "Support";

/** A single job as returned by the /api/v1/jobs endpoints. Traits are in the job's display order. */
export interface Job {
  id: string;
  jobName: string;
  jobDesc: string | null;
  primaryCategory: JobCategory | null;
  /** Every category on the job, primary first. */
  categories: JobCategory[];
  traits: Trait[];
  contentSourceName: string | null;
}

/** A Spring Data page of jobs. */
export interface JobPage {
  content: Job[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
