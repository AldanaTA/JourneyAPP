package com.journey.api_rest_v1.domain.creatures;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

/**
 * Composite primary key for {@code creature_augments}: a creature lists each target once per kind.
 */
@Embeddable
public class CreatureAugmentId implements Serializable {

    @Column(name = "creature_id", nullable = false, columnDefinition = "uuid")
    private UUID creatureId;

    @Convert(converter = AugmentKindConverter.class)
    @Column(name = "kind", nullable = false)
    private AugmentKind kind;

    @Column(name = "target", nullable = false)
    private String target;

    /** No-args constructor required by JPA. */
    public CreatureAugmentId() {
    }

    /** Creates a key for the given creature, kind, and target. */
    public CreatureAugmentId(UUID creatureId, AugmentKind kind, String target) {
        this.creatureId = creatureId;
        this.kind = kind;
        this.target = target;
    }

    /** Returns the creature half of the key. */
    public UUID getCreatureId() {
        return creatureId;
    }

    /** Returns whether the creature is immune, resistant, or weak. */
    public AugmentKind getKind() {
        return kind;
    }

    /** Returns the name of the damage type or status condition. */
    public String getTarget() {
        return target;
    }

    /** Keys are equal when creature, kind, and target all match. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreatureAugmentId that = (CreatureAugmentId) o;
        return Objects.equals(creatureId, that.creatureId) && kind == that.kind && Objects.equals(target, that.target);
    }

    /** Hash code consistent with {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return Objects.hash(creatureId, kind, target);
    }
}
