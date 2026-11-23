package com.journey.api_rest_v1.domain.jobs;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code job_category_assignments} join table.
 * Each row links one job to one {@link JobCategory}; at most one link per job is primary.
 */
@Entity
@Table(name = "job_category_assignments")
public class JobCategoryAssignment {

    @EmbeddedId
    private JobCategoryAssignmentId id;

    @MapsId("jobId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @MapsId("categoryId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private JobCategory category;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Creates a fully populated job/category link. */
    public JobCategoryAssignment(JobCategoryAssignmentId id, Job job, JobCategory category, boolean primary,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.job = job;
        this.category = category;
        this.primary = primary;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** No-args constructor required by JPA. */
    public JobCategoryAssignment() {
    }

    /** Returns the composite key of this link. */
    public JobCategoryAssignmentId getId() {
        return id;
    }

    /** Sets the composite key of this link. */
    public void setId(JobCategoryAssignmentId id) {
        this.id = id;
    }

    /** Returns the job that owns this link. */
    public Job getJob() {
        return job;
    }

    /** Sets the job that owns this link. */
    public void setJob(Job job) {
        this.job = job;
    }

    /** Returns the linked category. */
    public JobCategory getCategory() {
        return category;
    }

    /** Sets the linked category. */
    public void setCategory(JobCategory category) {
        this.category = category;
    }

    /** Returns true when this is the job's primary category. */
    public boolean isPrimary() {
        return primary;
    }

    /** Marks whether this is the job's primary category. */
    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    /** Returns when this link was created. */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** Returns when this link was last updated. */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** Sets when this link was last updated. */
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
