package com.journey.api_rest_v1.domain.traits;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

/**
 * Composite primary key for the {@code trait_categories} join table.
 * A trait can have each category at most once, so the key is (trait_id, category).
 */
@Embeddable
public class TraitCategoriesId implements Serializable {

    @Column(name = "trait_id", nullable = false, columnDefinition = "uuid")
    private UUID traitId;

    @Convert(converter = TraitCategoryConverter.class)
    @Column(name = "category", nullable = false)
    private TraitCategory category;

    /** No-args constructor required by JPA. */
    public TraitCategoriesId() {
    }

    /** Creates a key for the given trait and category. */
    public TraitCategoriesId(UUID traitId, TraitCategory category) {
        this.traitId = traitId;
        this.category = category;
    }

    /** Returns the trait half of the key. */
    public UUID getTraitId() {
        return traitId;
    }

    /** Sets the trait half of the key. */
    public void setTraitId(UUID traitId) {
        this.traitId = traitId;
    }

    /** Returns the category half of the key. */
    public TraitCategory getCategory() {
        return category;
    }

    /** Sets the category half of the key. */
    public void setCategory(TraitCategory category) {
        this.category = category;
    }

    /** Two keys are equal when both the trait id and category match. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraitCategoriesId that = (TraitCategoriesId) o;
        return Objects.equals(traitId, that.traitId) && category == that.category;
    }

    /** Hash code consistent with {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return Objects.hash(traitId, category);
    }
}
