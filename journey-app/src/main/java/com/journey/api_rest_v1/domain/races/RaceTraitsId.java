package com.journey.api_rest_v1.domain.races;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Composite primary key for the {@code race_traits} join table.
 * A race can include each trait at most once, so the key is (race_id, trait_id).
 */
@Embeddable
public class RaceTraitsId implements Serializable {

    @Column(name = "race_id", nullable = false, columnDefinition = "uuid")
    private UUID raceId;

    @Column(name = "trait_id", nullable = false, columnDefinition = "uuid")
    private UUID traitId;

    /** No-args constructor required by JPA. */
    public RaceTraitsId() {
    }

    /** Creates a key for the given race and trait. */
    public RaceTraitsId(UUID raceId, UUID traitId) {
        this.raceId = raceId;
        this.traitId = traitId;
    }

    /** Returns the race half of the key. */
    public UUID getRaceId() {
        return raceId;
    }

    /** Sets the race half of the key. */
    public void setRaceId(UUID raceId) {
        this.raceId = raceId;
    }

    /** Returns the trait half of the key. */
    public UUID getTraitId() {
        return traitId;
    }

    /** Sets the trait half of the key. */
    public void setTraitId(UUID traitId) {
        this.traitId = traitId;
    }

    /** Two keys are equal when both the race id and trait id match. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RaceTraitsId that = (RaceTraitsId) o;
        return Objects.equals(raceId, that.raceId) && Objects.equals(traitId, that.traitId);
    }

    /** Hash code consistent with {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return Objects.hash(raceId, traitId);
    }
}
