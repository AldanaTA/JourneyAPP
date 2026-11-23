import type { Job } from "../../../../models/Job";

/** Describes how many traits a job has, e.g. "4 traits". */
export function formatJobTraitCount(job: Job): string {
  return `${job.traits.length} trait${job.traits.length === 1 ? "" : "s"}`;
}

/** Sums the EXP purchase cost of every trait in the job (traits without a cost count as 0). */
export function totalJobPurchaseCost(job: Job): number {
  return job.traits.reduce((total, trait) => total + (trait.purchaseCost ?? 0), 0);
}

/** Returns the job's secondary categories (every category except the primary one). */
export function secondaryJobCategories(job: Job): Job["categories"] {
  return job.categories.filter((category) => category !== job.primaryCategory);
}
