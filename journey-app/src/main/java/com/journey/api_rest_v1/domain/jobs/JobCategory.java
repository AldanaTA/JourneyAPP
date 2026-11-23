package com.journey.api_rest_v1.domain.jobs;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code job_categories} lookup table.
 * Unlike trait categories (a Postgres enum), job categories are rows seeded by the schema,
 * so they are referenced by their {@code category} name (e.g. "Caster").
 */
@Entity
@Table(name = "job_categories")
public class JobCategory {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public JobCategory() {
    }

    /** Returns the category's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Returns the display name of the category (e.g. "Half-Caster"). */
    public String getCategory() {
        return category;
    }

    /** Sets the display name of the category. */
    public void setCategory(String category) {
        this.category = category;
    }

    /** Returns the description of what jobs in this category focus on. */
    public String getDescription() {
        return description;
    }

    /** Sets the description of what jobs in this category focus on. */
    public void setDescription(String description) {
        this.description = description;
    }

    /** Returns when the category was created. */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** Returns when the category was last updated. */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
