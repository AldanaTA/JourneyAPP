package com.journey.api_rest_v1.domain.items;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code weapon_types} reference table (e.g. Bow -> Nimble Shot).
 * Seeded by the schema; read-only from the API's point of view.
 */
@Entity
@Table(name = "weapon_types")
public class WeaponType {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_key", insertable = false, updatable = false)
    private String nameKey;

    @Column(name = "effect_name", nullable = false)
    private String effectName;

    @Column(name = "effect", nullable = false, columnDefinition = "TEXT")
    private String effect;

    /** No-args constructor required by JPA. */
    public WeaponType() {
    }

    /** Returns the weapon type's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Returns the display name (e.g. "Bow"). */
    public String getName() {
        return name;
    }

    /** Returns the database-generated lowercase/trimmed name used for matching. */
    public String getNameKey() {
        return nameKey;
    }

    /** Returns the name of the effect every weapon of this type grants (e.g. "Nimble Shot"). */
    public String getEffectName() {
        return effectName;
    }

    /** Returns the rules text of that effect. */
    public String getEffect() {
        return effect;
    }
}
