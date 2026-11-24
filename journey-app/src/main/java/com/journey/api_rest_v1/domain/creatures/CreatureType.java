package com.journey.api_rest_v1.domain.creatures;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.journey.api_rest_v1.domain.content_source.ContentSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code creature_types} table. Types are defined per content source,
 * and a creature must use a type from its own source.
 */
@Entity
@Table(name = "creature_types")
public class CreatureType {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private ContentSource source;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_key", insertable = false, updatable = false)
    private String nameKey;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public CreatureType() {
    }

    /** Returns the type's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Returns the content source that defines this type. */
    public ContentSource getSource() {
        return source;
    }

    /** Returns the display name (e.g. "Humanoid"). */
    public String getName() {
        return name;
    }

    /** Returns the database-generated lowercase/trimmed name used for matching. */
    public String getNameKey() {
        return nameKey;
    }

    /** Returns the description of the type. */
    public String getDescription() {
        return description;
    }
}
