package com.journey.api_rest_v1.domain.traits;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.journey.api_rest_v1.domain.content_source.ContentSource;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code traits} table.
 * A trait is a purchasable character feature with a level, purchase/level-up costs,
 * optional resource costs, an effect, and a set of categories used for filtering.
 */
@Entity
@Table(name = "traits")
public class Trait {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "trait_name", nullable = false)
    private String traitName;

    @Column(name = "trait_name_key", insertable = false, updatable = false)
    private String traitNameKey;

    @Column(name = "lvl", nullable = false)
    private Integer lvl;

    @Column(name = "purchase_cost", nullable = false)
    private Integer purchaseCost;

    @Column(name = "lvl_up_cost", nullable = false)
    private Integer lvlUpCost;

    @Column(name = "trait_desc", nullable = false, columnDefinition = "TEXT")
    private String traitDesc;

    @Column(name = "trait_uses")
    private Integer traitUses;

    @Column(name = "mp_cost")
    private Integer mpCost;

    @Column(name = "ep_cost")
    private Integer epCost;

    @Column(name = "tp_cost")
    private Integer tpCost;

    @Column(name = "trait_effect", nullable = false, columnDefinition = "TEXT")
    private String traitEffect;

    @Column(name = "lvl_up_effect")
    private String lvlUpEffect;

    @OneToMany(mappedBy = "trait", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TraitCategories> categories = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "source_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_traits_content_source")
    )
    private ContentSource source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public Trait() {
    }

    /** Creates a fully populated trait; a null category list is replaced with an empty one. */
    public Trait(UUID id, String traitName, Integer lvl, Integer purchaseCost, Integer lvlUpCost, String traitDesc,
            Integer traitUses, Integer mpCost, Integer epCost, Integer tpCost, String traitEffect, String lvlUpEffect,
            List<TraitCategories> categories, ContentSource source, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.traitName = traitName;
        this.lvl = lvl;
        this.purchaseCost = purchaseCost;
        this.lvlUpCost = lvlUpCost;
        this.traitDesc = traitDesc;
        this.traitUses = traitUses;
        this.mpCost = mpCost;
        this.epCost = epCost;
        this.tpCost = tpCost;
        this.traitEffect = traitEffect;
        this.lvlUpEffect = lvlUpEffect;
        this.categories = categories == null ? new ArrayList<>() : categories;
        this.source = source;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Returns the trait's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Sets the trait's UUID primary key. */
    public void setId(UUID id) {
        this.id = id;
    }

    /** Returns the display name of the trait. */
    public String getTraitName() {
        return traitName;
    }

    /** Sets the display name of the trait. */
    public void setTraitName(String traitName) {
        this.traitName = traitName;
    }

    /** Returns the database-generated lowercase/trimmed name used for uniqueness. */
    public String getTraitNameKey() {
        return traitNameKey;
    }

    /** Returns the level requirement of the trait. */
    public Integer getLvl() {
        return lvl;
    }

    /** Sets the level requirement of the trait. */
    public void setLvl(Integer lvl) {
        this.lvl = lvl;
    }

    /** Returns the cost to purchase the trait. */
    public Integer getPurchaseCost() {
        return purchaseCost;
    }

    /** Sets the cost to purchase the trait. */
    public void setPurchaseCost(Integer purchaseCost) {
        this.purchaseCost = purchaseCost;
    }

    /** Returns the cost to level the trait up. */
    public Integer getLvlUpCost() {
        return lvlUpCost;
    }

    /** Sets the cost to level the trait up. */
    public void setLvlUpCost(Integer lvlUpCost) {
        this.lvlUpCost = lvlUpCost;
    }

    /** Returns the flavor/summary description of the trait. */
    public String getTraitDesc() {
        return traitDesc;
    }

    /** Sets the flavor/summary description of the trait. */
    public void setTraitDesc(String traitDesc) {
        this.traitDesc = traitDesc;
    }

    /** Returns how many times the trait can be used (0 means unlimited/passive). */
    public Integer getTraitUses() {
        return traitUses;
    }

    /** Sets how many times the trait can be used. */
    public void setTraitUses(Integer traitUses) {
        this.traitUses = traitUses;
    }

    /** Returns the MP cost to activate the trait. */
    public Integer getMpCost() {
        return mpCost;
    }

    /** Sets the MP cost to activate the trait. */
    public void setMpCost(Integer mpCost) {
        this.mpCost = mpCost;
    }

    /** Returns the EP cost to activate the trait. */
    public Integer getEpCost() {
        return epCost;
    }

    /** Sets the EP cost to activate the trait. */
    public void setEpCost(Integer epCost) {
        this.epCost = epCost;
    }

    /** Returns the TP cost to activate the trait. */
    public Integer getTpCost() {
        return tpCost;
    }

    /** Sets the TP cost to activate the trait. */
    public void setTpCost(Integer tpCost) {
        this.tpCost = tpCost;
    }

    /** Returns the rules text describing what the trait does. */
    public String getTraitEffect() {
        return traitEffect;
    }

    /** Sets the rules text describing what the trait does. */
    public void setTraitEffect(String traitEffect) {
        this.traitEffect = traitEffect;
    }

    /** Returns the additional effect gained when the trait is leveled up. */
    public String getLvlUpEffect() {
        return lvlUpEffect;
    }

    /** Sets the additional effect gained when the trait is leveled up. */
    public void setLvlUpEffect(String lvlUpEffect) {
        this.lvlUpEffect = lvlUpEffect;
    }

    /** Returns the category links attached to this trait. */
    public List<TraitCategories> getCategories() {
        return categories;
    }

    /** Replaces the category links; a null list is replaced with an empty one. */
    public void setCategories(List<TraitCategories> categories) {
        this.categories = categories == null ? new ArrayList<>() : categories;
    }

    /** Returns the content source (book/expansion) this trait belongs to. */
    public ContentSource getSource() {
        return source;
    }

    /** Sets the content source (book/expansion) this trait belongs to. */
    public void setSource(ContentSource source) {
        this.source = source;
    }

    /** Returns when the trait was created. */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** Returns when the trait was last updated. */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** Sets when the trait was last updated. */
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
