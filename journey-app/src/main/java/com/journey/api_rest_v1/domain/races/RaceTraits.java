package com.journey.api_rest_v1.domain.races;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.journey.api_rest_v1.domain.traits.Trait;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code race_traits} join table.
 * Each row links one race to one existing {@link Trait}, with a sort order for display.
 * {@code trait_name} is a denormalized copy of the trait's name, kept in sync when links are written.
 */
@Entity
@Table(name = "race_traits")
public class RaceTraits {

    @EmbeddedId
    private RaceTraitsId id;

    @MapsId("raceId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @MapsId("traitId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trait_id", nullable = false)
    private Trait trait;

    @Column(name = "trait_name", nullable = false)
    private String traitName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Creates a fully populated race/trait link. */
    public RaceTraits(RaceTraitsId id, Race race, Trait trait, String traitName, Integer sortOrder,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.race = race;
        this.trait = trait;
        this.traitName = traitName;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** No-args constructor required by JPA. */
    public RaceTraits() {
    }

    /** Returns the composite key of this link. */
    public RaceTraitsId getId() {
        return id;
    }

    /** Sets the composite key of this link. */
    public void setId(RaceTraitsId id) {
        this.id = id;
    }

    /** Returns the race that owns this link. */
    public Race getRace() {
        return race;
    }

    /** Sets the race that owns this link. */
    public void setRace(Race race) {
        this.race = race;
    }

    /** Returns the linked trait. */
    public Trait getTrait() {
        return trait;
    }

    /** Sets the linked trait. */
    public void setTrait(Trait trait) {
        this.trait = trait;
    }

    /** Returns the trait name stored on the link. */
    public String getTraitName() {
        return traitName;
    }

    /** Sets the trait name stored on the link. */
    public void setTraitName(String traitName) {
        this.traitName = traitName;
    }

    /** Returns the display position of the trait within the race (0-based). */
    public Integer getSortOrder() {
        return sortOrder;
    }

    /** Sets the display position of the trait within the race. */
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** Returns when this link was created. */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** Returns when this link was last updated. */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** Sets when this link was last updated. */
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
