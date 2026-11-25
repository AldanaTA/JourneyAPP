package com.journey.api_rest_v1.domain.rules;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.journey.api_rest_v1.domain.content_source.ContentSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code books} table. A book lists its chapters, in order, through
 * {@code book_chapters}, so a setting book can include chapters from another source by reference.
 */
@Entity
@Table(name = "books")
public class Book {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private ContentSource source;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "title_key", insertable = false, updatable = false)
    private String titleKey;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "book", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 20)
    private List<BookChapter> chapters = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public Book() {
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

    public String getTitleKey() {
        return titleKey;
    }

    public String getDescription() {
        return description;
    }

    /** The book's chapters in reading order. */
    public List<BookChapter> getChapters() {
        return chapters;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
