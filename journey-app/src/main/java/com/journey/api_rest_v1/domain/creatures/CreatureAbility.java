package com.journey.api_rest_v1.domain.creatures;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.traits.Trait;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in {@code creature_abilities}: a passive, triggered, or declared ability with the
 * creature's own (condensed) text, optionally linked to the full game trait or power it comes from.
 */
@Entity
@Table(name = "creature_abilities")
public class CreatureAbility {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creature_id", nullable = false)
    private Creature creature;

    @Convert(converter = AbilityTimingConverter.class)
    @Column(name = "timing", nullable = false)
    private AbilityTiming timing;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "effect", nullable = false, columnDefinition = "TEXT")
    private String effect;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trait_id")
    private Trait trait;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "power_id")
    private Power power;

    @Column(name = "lvl")
    private Integer lvl;

    @Column(name = "uses")
    private Integer uses;

    @Column(name = "mp_cost")
    private Integer mpCost;

    @Column(name = "ep_cost")
    private Integer epCost;

    @Column(name = "tp_cost")
    private Integer tpCost;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public CreatureAbility() {
    }

    /** Creates an ability for the creature. */
    public CreatureAbility(Creature creature, AbilityTiming timing, String name, String effect, Trait trait,
            Power power, Integer lvl, Integer uses, Integer mpCost, Integer epCost, Integer tpCost, Integer sortOrder) {
        this.creature = creature;
        this.timing = timing;
        this.name = name;
        this.effect = effect;
        this.trait = trait;
        this.power = power;
        this.lvl = lvl;
        this.uses = uses;
        this.mpCost = mpCost;
        this.epCost = epCost;
        this.tpCost = tpCost;
        this.sortOrder = sortOrder;
    }

    /** Returns the ability's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Returns when the ability applies (passive, triggered, declared). */
    public AbilityTiming getTiming() {
        return timing;
    }

    /** Returns the ability name. */
    public String getName() {
        return name;
    }

    /** Returns the creature's own text for the ability. */
    public String getEffect() {
        return effect;
    }

    /** Returns the linked game trait, or null. */
    public Trait getTrait() {
        return trait;
    }

    /** Returns the linked game power, or null. */
    public Power getPower() {
        return power;
    }

    /** Returns the trait/power level, or null. */
    public Integer getLvl() {
        return lvl;
    }

    /** Returns the number of uses, or null. */
    public Integer getUses() {
        return uses;
    }

    /** Returns the MP cost, or null. */
    public Integer getMpCost() {
        return mpCost;
    }

    /** Returns the EP cost, or null. */
    public Integer getEpCost() {
        return epCost;
    }

    /** Returns the TP cost, or null. */
    public Integer getTpCost() {
        return tpCost;
    }

    /** Returns the display position within the creature. */
    public Integer getSortOrder() {
        return sortOrder;
    }
}
