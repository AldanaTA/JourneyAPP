package com.journey.api_rest_v1.domain.rules;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code rule_sections} table: one heading and the Markdown under it.
 * The tree is kept as plain ids ({@code chapterId}, {@code parentId}) because a chapter is always read whole,
 * in {@code sort_order}. The generated {@code search_vector} column is only used by native search queries.
 */
@Entity
@Table(name = "rule_sections")
public class RuleSection {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "chapter_id", nullable = false, columnDefinition = "uuid")
    private UUID chapterId;

    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    /** Heading level, 1-6. */
    @Column(name = "level", nullable = false)
    private Integer level;

    /** URL-friendly id for deep links, unique within the chapter. */
    @Column(name = "slug", nullable = false, columnDefinition = "TEXT")
    private String slug;

    /** Markdown with {@code (trait:<id>)}-style entity links and {@code (asset:<id>)} images. */
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public RuleSection() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getChapterId() {
        return chapterId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getTitle() {
        return title;
    }

    public Integer getLevel() {
        return level;
    }

    public String getSlug() {
        return slug;
    }

    public String getBody() {
        return body;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
