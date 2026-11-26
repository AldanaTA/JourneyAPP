package com.journey.api_rest_v1.domain.characters;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code character_races} table: one of a character's races (several for a mixed race).
 */
@Entity
@Table(name = "character_races")
public class CharacterRace {

    @EmbeddedId
    @AttributeOverride(name = "childId", column = @Column(name = "race_id", nullable = false, columnDefinition = "uuid"))
    private CharacterChildId id;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** No-args constructor required by JPA. */
    public CharacterRace() {
    }

    public CharacterChildId getId() {
        return id;
    }

    public void setId(CharacterChildId id) {
        this.id = id;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
