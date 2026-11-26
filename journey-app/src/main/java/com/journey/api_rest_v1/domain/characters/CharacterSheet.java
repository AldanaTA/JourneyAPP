package com.journey.api_rest_v1.domain.characters;

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
 * JPA entity for a row in the {@code character_sheets} table: one starting sheet (Physical, Magic, Balanced)
 * and the attribute values it starts with. Read-only here; the sheets are seeded by the schema.
 */
@Entity
@Table(name = "character_sheets")
public class CharacterSheet {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "source_id", nullable = false, columnDefinition = "uuid")
    private UUID sourceId;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "name_key", insertable = false, updatable = false)
    private String nameKey;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_body", nullable = false)
    private Integer baseBody;

    @Column(name = "base_strength", nullable = false)
    private Integer baseStrength;

    @Column(name = "base_speed", nullable = false)
    private Integer baseSpeed;

    @Column(name = "base_magic", nullable = false)
    private Integer baseMagic;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public CharacterSheet() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getDescription() {
        return description;
    }

    public Integer getBaseBody() {
        return baseBody;
    }

    public Integer getBaseStrength() {
        return baseStrength;
    }

    public Integer getBaseSpeed() {
        return baseSpeed;
    }

    public Integer getBaseMagic() {
        return baseMagic;
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
