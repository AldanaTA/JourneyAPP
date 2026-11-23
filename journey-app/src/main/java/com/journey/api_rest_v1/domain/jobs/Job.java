package com.journey.api_rest_v1.domain.jobs;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.journey.api_rest_v1.domain.content_source.ContentSource;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code jobs} table.
 * A job is a character profession made up of an ordered list of existing traits,
 * tagged with one primary category and optional secondary categories.
 */
@Entity
@Table(name = "jobs")
public class Job {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String jobName;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String jobDesc;

    // Batch-loaded so a page of jobs does not issue one query per job.
    @BatchSize(size = 50)
    @OrderBy("sortOrder ASC")
    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobTraits> traits = new ArrayList<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobCategoryAssignment> categories = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "source_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_jobs_content_source")
    )
    private ContentSource source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public Job() {
    }

    /** Creates a fully populated job; null link lists are replaced with empty ones. */
    public Job(UUID id, String jobName, String jobDesc, List<JobTraits> traits,
            List<JobCategoryAssignment> categories, ContentSource source, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.jobName = jobName;
        this.jobDesc = jobDesc;
        this.traits = traits == null ? new ArrayList<>() : traits;
        this.categories = categories == null ? new ArrayList<>() : categories;
        this.source = source;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Returns the job's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Sets the job's UUID primary key. */
    public void setId(UUID id) {
        this.id = id;
    }

    /** Returns the display name of the job. */
    public String getJobName() {
        return jobName;
    }

    /** Sets the display name of the job. */
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    /** Returns the flavor description of the job. */
    public String getJobDesc() {
        return jobDesc;
    }

    /** Sets the flavor description of the job. */
    public void setJobDesc(String jobDesc) {
        this.jobDesc = jobDesc;
    }

    /** Returns the trait links attached to this job, ordered by sort order. */
    public List<JobTraits> getTraits() {
        return traits;
    }

    /** Replaces the trait links; a null list is replaced with an empty one. */
    public void setTraits(List<JobTraits> traits) {
        this.traits = traits == null ? new ArrayList<>() : traits;
    }

    /** Returns the category links attached to this job. */
    public List<JobCategoryAssignment> getCategories() {
        return categories;
    }

    /** Replaces the category links; a null list is replaced with an empty one. */
    public void setCategories(List<JobCategoryAssignment> categories) {
        this.categories = categories == null ? new ArrayList<>() : categories;
    }

    /** Returns the content source (book/expansion) this job belongs to. */
    public ContentSource getSource() {
        return source;
    }

    /** Sets the content source (book/expansion) this job belongs to. */
    public void setSource(ContentSource source) {
        this.source = source;
    }

    /** Returns when the job was created. */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** Returns when the job was last updated. */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** Sets when the job was last updated. */
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
