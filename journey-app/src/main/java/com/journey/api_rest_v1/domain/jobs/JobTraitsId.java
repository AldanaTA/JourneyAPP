package com.journey.api_rest_v1.domain.jobs;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Composite primary key for the {@code job_traits} join table.
 * A job can include each trait at most once, so the key is (job_id, trait_id).
 */
@Embeddable
public class JobTraitsId implements Serializable {

    @Column(name = "job_id", nullable = false, columnDefinition = "uuid")
    private UUID jobId;

    @Column(name = "trait_id", nullable = false, columnDefinition = "uuid")
    private UUID traitId;

    /** No-args constructor required by JPA. */
    public JobTraitsId() {
    }

    /** Creates a key for the given job and trait. */
    public JobTraitsId(UUID jobId, UUID traitId) {
        this.jobId = jobId;
        this.traitId = traitId;
    }

    /** Returns the job half of the key. */
    public UUID getJobId() {
        return jobId;
    }

    /** Sets the job half of the key. */
    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    /** Returns the trait half of the key. */
    public UUID getTraitId() {
        return traitId;
    }

    /** Sets the trait half of the key. */
    public void setTraitId(UUID traitId) {
        this.traitId = traitId;
    }

    /** Two keys are equal when both the job id and trait id match. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobTraitsId that = (JobTraitsId) o;
        return Objects.equals(jobId, that.jobId) && Objects.equals(traitId, that.traitId);
    }

    /** Hash code consistent with {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return Objects.hash(jobId, traitId);
    }
}
