package com.journey.api_rest_v1.domain.rules;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.journey.api_rest_v1.domain.content_source.ContentSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code rule_chapters} table. A chapter belongs to one content source; its
 * sections are loaded separately, in reading order, through {@code RuleSectionRepository}.
 */
@Entity
@Table(name = "rule_chapters")
public class RuleChapter {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private ContentSource source;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    /** Stable identity used by the importer ("04_Combat(Journey 100).docx" -> "combat"). */
    @Column(name = "chapter_key", nullable = false, columnDefinition = "TEXT")
    private String chapterKey;

    /** The number from the file name; null for chapters created in the web app. */
    @Column(name = "number")
    private Integer number;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public RuleChapter() {
    }

    public UUID getId() {
        return id;
    }

    public ContentSource getSource() {
        return source;
    }

    public String getTitle() {
        return title;
    }

    public String getChapterKey() {
        return chapterKey;
    }

    public Integer getNumber() {
        return number;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
