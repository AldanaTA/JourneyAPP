package com.journey.api_rest_v1.domain.creatures;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.journey.api_rest_v1.domain.items.DamageType;
import com.journey.api_rest_v1.domain.items.WeaponProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in {@code creature_attacks}: a declared attack with its skill, range, damage,
 * and weapon properties (shared with item weapons).
 */
@Entity
@Table(name = "creature_attacks")
public class CreatureAttack {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creature_id", nullable = false)
    private Creature creature;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "skill", nullable = false)
    private String skill;

    @Column(name = "range_ft")
    private Integer rangeFt;

    @Column(name = "damage_dice", nullable = false)
    private String damageDice;

    @Column(name = "damage_bonus")
    private String damageBonus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "damage_type_id", nullable = false)
    private DamageType damageType;

    @Column(name = "mag")
    private Integer mag;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @ManyToMany
    @JoinTable(
        name = "creature_attack_properties",
        joinColumns = @JoinColumn(name = "attack_id"),
        inverseJoinColumns = @JoinColumn(name = "property_id")
    )
    private Set<WeaponProperty> properties = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public CreatureAttack() {
    }

    /** Creates an attack for the creature. */
    public CreatureAttack(Creature creature, String name, String skill, Integer rangeFt, String damageDice,
            String damageBonus, DamageType damageType, Integer mag, Integer sortOrder, Set<WeaponProperty> properties) {
        this.creature = creature;
        this.name = name;
        this.skill = skill;
        this.rangeFt = rangeFt;
        this.damageDice = damageDice;
        this.damageBonus = damageBonus;
        this.damageType = damageType;
        this.mag = mag;
        this.sortOrder = sortOrder;
        this.properties = properties == null ? new LinkedHashSet<>() : properties;
    }

    /** Returns the attack's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Returns the attack name (e.g. "Bite"). */
    public String getName() {
        return name;
    }

    /** Returns the skill rolled to attack (e.g. "Brawl"). */
    public String getSkill() {
        return skill;
    }

    /** Returns the range in feet, or null. */
    public Integer getRangeFt() {
        return rangeFt;
    }

    /** Returns the damage dice (e.g. "1d8"). */
    public String getDamageDice() {
        return damageDice;
    }

    /** Returns the damage bonus (e.g. "12", "PD"), or null. */
    public String getDamageBonus() {
        return damageBonus;
    }

    /** Returns the damage type. */
    public DamageType getDamageType() {
        return damageType;
    }

    /** Returns the magazine size, or null. */
    public Integer getMag() {
        return mag;
    }

    /** Returns the display position within the creature. */
    public Integer getSortOrder() {
        return sortOrder;
    }

    /** Returns the weapon properties on this attack. */
    public Set<WeaponProperty> getProperties() {
        return properties;
    }
}
