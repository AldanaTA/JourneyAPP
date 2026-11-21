package com.journey.api_rest_v1.domain.traits;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code trait_categories} join table.
 * Each row links one trait to one {@link TraitCategory}.
 */
@Entity
@Table(name = "trait_categories")
public class TraitCategories {

    @EmbeddedId
    private TraitCategoriesId id;

    @MapsId("traitId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trait_id", nullable = false)
    private Trait trait;

    @Column(name = "category", nullable = false, insertable = false, updatable = false)
    @Convert(converter = TraitCategoryConverter.class)
    private TraitCategory category;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Creates a fully populated trait/category link. */
    public TraitCategories(TraitCategoriesId id, Trait trait, TraitCategory category, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.trait = trait;
        this.category = category;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** No-args constructor required by JPA. */
    public TraitCategories() {
    }

    /** Returns the composite key of this link. */
    public TraitCategoriesId getId() {
        return id;
    }

    /** Sets the composite key of this link. */
    public void setId(TraitCategoriesId id) {
        this.id = id;
    }

    /** Returns the trait that owns this link. */
    public Trait getTrait() {
        return trait;
    }

    /** Sets the trait that owns this link. */
    public void setTrait(Trait trait) {
        this.trait = trait;
    }

    /** Returns the category of this link. */
    public TraitCategory getCategory() {
        return category;
    }

    /** Sets the category of this link. */
    public void setCategory(TraitCategory category) {
        this.category = category;
    }

    /** Returns when this link was created. */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** Sets when this link was created. */
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
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
