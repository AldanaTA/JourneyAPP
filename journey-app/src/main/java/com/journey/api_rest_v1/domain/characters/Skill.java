package com.journey.api_rest_v1.domain.characters;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code skills} table: one skill of a setting, combat or life.
 * Read-only here; the skills are seeded by the schema.
 */
@Entity
@Table(name = "skills")
public class Skill {

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

    @Convert(converter = SkillCategoryConverter.class)
    @Column(name = "category", nullable = false)
    private SkillCategory category;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_rating", nullable = false)
    private Integer baseRating;

    @Column(name = "weapon_type_id", columnDefinition = "uuid")
    private UUID weaponTypeId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public Skill() {
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

    public SkillCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public Integer getBaseRating() {
        return baseRating;
    }

    public UUID getWeaponTypeId() {
        return weaponTypeId;
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
