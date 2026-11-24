package com.journey.api_rest_v1.domain.creatures;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.journey.api_rest_v1.domain.items.DamageType;
import com.journey.api_rest_v1.domain.items.StatusCondition;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in {@code creature_augments}: an immunity, resistance, or weakness to exactly one
 * damage type or status condition ({@link #getDamageType()} or {@link #getStatusCondition()}).
 */
@Entity
@Table(name = "creature_augments")
public class CreatureAugment {

    @EmbeddedId
    private CreatureAugmentId id;

    @MapsId("creatureId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creature_id", nullable = false)
    private Creature creature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "damage_type_id")
    private DamageType damageType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_condition_id")
    private StatusCondition statusCondition;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public CreatureAugment() {
    }

    /** Creates an augment against a damage type. */
    public CreatureAugment(Creature creature, AugmentKind kind, DamageType damageType) {
        this.id = new CreatureAugmentId(creature.getId(), kind, damageType.getName());
        this.creature = creature;
        this.damageType = damageType;
    }

    /** Creates an augment against a status condition. */
    public CreatureAugment(Creature creature, AugmentKind kind, StatusCondition statusCondition) {
        this.id = new CreatureAugmentId(creature.getId(), kind, statusCondition.getName());
        this.creature = creature;
        this.statusCondition = statusCondition;
    }

    /** Returns the composite key (creature, kind, target). */
    public CreatureAugmentId getId() {
        return id;
    }

    /** Returns the creature that has this augment. */
    public Creature getCreature() {
        return creature;
    }

    /** Returns the linked damage type, or null when the target is a status condition. */
    public DamageType getDamageType() {
        return damageType;
    }

    /** Returns the linked status condition, or null when the target is a damage type. */
    public StatusCondition getStatusCondition() {
        return statusCondition;
    }
}
