package com.journey.api_rest_v1.domain.rules;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code rule_section_entities} table: one entity referenced from a section body.
 * Exactly one of the entity id columns is set. Ids are kept as plain UUIDs so a chapter's references can be
 * grouped by type and loaded in one query per type.
 */
@Entity
@Table(name = "rule_section_entities")
public class RuleSectionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "section_id", nullable = false, columnDefinition = "uuid")
    private UUID sectionId;

    @Column(name = "trait_id", columnDefinition = "uuid")
    private UUID traitId;

    @Column(name = "power_id", columnDefinition = "uuid")
    private UUID powerId;

    @Column(name = "item_id", columnDefinition = "uuid")
    private UUID itemId;

    @Column(name = "race_id", columnDefinition = "uuid")
    private UUID raceId;

    @Column(name = "job_id", columnDefinition = "uuid")
    private UUID jobId;

    @Column(name = "creature_id", columnDefinition = "uuid")
    private UUID creatureId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** No-args constructor required by JPA. */
    public RuleSectionEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public UUID getTraitId() {
        return traitId;
    }

    public UUID getPowerId() {
        return powerId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public UUID getRaceId() {
        return raceId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getCreatureId() {
        return creatureId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
