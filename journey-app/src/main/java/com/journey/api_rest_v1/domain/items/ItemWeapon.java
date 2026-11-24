package com.journey.api_rest_v1.domain.items;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code item_weapons} table: an item's weapon stats.
 * Base weapons always have one; a derived item has one only when it overrides its base's stats.
 */
@Entity
@Table(name = "item_weapons")
public class ItemWeapon {

    @Id
    @Column(name = "item_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID itemId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "weapon_type_id", nullable = false)
    private WeaponType weaponType;

    @Column(name = "damage_dice", nullable = false)
    private String damageDice;

    @Column(name = "damage_bonus")
    private String damageBonus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "damage_type_id", nullable = false)
    private DamageType damageType;

    @Column(name = "range_ft")
    private Integer rangeFt;

    @Column(name = "mag")
    private Integer mag;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public ItemWeapon() {
    }

    /** Creates weapon stats for the given item. */
    public ItemWeapon(Item item, WeaponType weaponType, String damageDice, String damageBonus, DamageType damageType,
            Integer rangeFt, Integer mag) {
        this.item = item;
        this.weaponType = weaponType;
        this.damageDice = damageDice;
        this.damageBonus = damageBonus;
        this.damageType = damageType;
        this.rangeFt = rangeFt;
        this.mag = mag;
    }

    /** Returns the owning item's id (also this row's primary key). */
    public UUID getItemId() {
        return itemId;
    }

    /** Returns the item these stats belong to. */
    public Item getItem() {
        return item;
    }

    /** Returns the weapon type (e.g. Martial). */
    public WeaponType getWeaponType() {
        return weaponType;
    }

    /** Sets the weapon type. */
    public void setWeaponType(WeaponType weaponType) {
        this.weaponType = weaponType;
    }

    /** Returns the damage dice (e.g. "2d6"). */
    public String getDamageDice() {
        return damageDice;
    }

    /** Sets the damage dice. */
    public void setDamageDice(String damageDice) {
        this.damageDice = damageDice;
    }

    /** Returns the flat or stat damage bonus (e.g. "5", "PD", "MD"), or null. */
    public String getDamageBonus() {
        return damageBonus;
    }

    /** Sets the damage bonus. */
    public void setDamageBonus(String damageBonus) {
        this.damageBonus = damageBonus;
    }

    /** Returns the damage type (e.g. Slashing). */
    public DamageType getDamageType() {
        return damageType;
    }

    /** Sets the damage type. */
    public void setDamageType(DamageType damageType) {
        this.damageType = damageType;
    }

    /** Returns the range in feet, or null for melee weapons. */
    public Integer getRangeFt() {
        return rangeFt;
    }

    /** Sets the range in feet. */
    public void setRangeFt(Integer rangeFt) {
        this.rangeFt = rangeFt;
    }

    /** Returns the magazine size, or null when the weapon has none. */
    public Integer getMag() {
        return mag;
    }

    /** Sets the magazine size. */
    public void setMag(Integer mag) {
        this.mag = mag;
    }
}
