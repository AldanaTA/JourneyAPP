package com.journey.api_rest_v1.domain.jobs;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Composite primary key for the {@code job_category_assignments} join table.
 * A job can have each category at most once, so the key is (job_id, category_id).
 */
@Embeddable
public class JobCategoryAssignmentId implements Serializable {

    @Column(name = "job_id", nullable = false, columnDefinition = "uuid")
    private UUID jobId;

    @Column(name = "category_id", nullable = false, columnDefinition = "uuid")
    private UUID categoryId;

    /** No-args constructor required by JPA. */
    public JobCategoryAssignmentId() {
    }

    /** Creates a key for the given job and category. */
    public JobCategoryAssignmentId(UUID jobId, UUID categoryId) {
        this.jobId = jobId;
        this.categoryId = categoryId;
    }

    /** Returns the job half of the key. */
    public UUID getJobId() {
        return jobId;
    }

    /** Sets the job half of the key. */
    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    /** Returns the category half of the key. */
    public UUID getCategoryId() {
        return categoryId;
    }

    /** Sets the category half of the key. */
    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    /** Two keys are equal when both the job id and category id match. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobCategoryAssignmentId that = (JobCategoryAssignmentId) o;
        return Objects.equals(jobId, that.jobId) && Objects.equals(categoryId, that.categoryId);
    }

    /** Hash code consistent with {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return Objects.hash(jobId, categoryId);
    }
}
