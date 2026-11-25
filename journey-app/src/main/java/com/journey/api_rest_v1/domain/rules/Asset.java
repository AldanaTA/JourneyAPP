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
 * JPA entity for a row in the {@code assets} table: an image stored on disk under the asset storage
 * directory. Rule bodies refer to it as {@code ![alt](asset:<id>)}. Assets are written by the rules importer,
 * so this entity is read-only here.
 */
@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private ContentSource source;

    /** Path relative to the storage root, e.g. "core/3f/3f2a...c1.png". */
    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "original_filename", columnDefinition = "TEXT")
    private String originalFilename;

    @Column(name = "content_type", nullable = false, columnDefinition = "TEXT")
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "alt_text", columnDefinition = "TEXT")
    private String altText;

    @Column(name = "sha256", nullable = false, columnDefinition = "TEXT")
    private String sha256;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public Asset() {
    }

    public UUID getId() {
        return id;
    }

    public ContentSource getSource() {
        return source;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getByteSize() {
        return byteSize;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public String getAltText() {
        return altText;
    }

    public String getSha256() {
        return sha256;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
