package com.journey.api_rest_v1.domain.jobs;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.journey.api_rest_v1.domain.traits.Trait;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code job_traits} join table.
 * Each row links one job to one existing {@link Trait}, with a sort order for display.
 */
@Entity
@Table(name = "job_traits")
public class JobTraits {

    @EmbeddedId
    private JobTraitsId id;

    @MapsId("jobId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @MapsId("traitId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trait_id", nullable = false)
    private Trait trait;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Creates a fully populated job/trait link. */
    public JobTraits(JobTraitsId id, Job job, Trait trait, Integer sortOrder, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.job = job;
        this.trait = trait;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** No-args constructor required by JPA. */
    public JobTraits() {
    }

    /** Returns the composite key of this link. */
    public JobTraitsId getId() {
        return id;
    }

    /** Sets the composite key of this link. */
    public void setId(JobTraitsId id) {
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

    /** Returns the linked trait. */
    public Trait getTrait() {
        return trait;
    }

    /** Sets the linked trait. */
    public void setTrait(Trait trait) {
        this.trait = trait;
    }

    /** Returns the display position of the trait within the job (0-based). */
    public Integer getSortOrder() {
        return sortOrder;
    }

    /** Sets the display position of the trait within the job. */
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
