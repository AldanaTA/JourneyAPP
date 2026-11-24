package com.journey.api_rest_v1.domain.items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code damage_types} reference table (Acid, Fire, Slashing, ...).
 * Seeded by the schema; read-only from the API's point of view.
 */
@Entity
@Table(name = "damage_types")
public class DamageType {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_key", insertable = false, updatable = false)
    private String nameKey;

    @Column(name = "effect", nullable = false, columnDefinition = "TEXT")
    private String effect;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "defenses", nullable = false, columnDefinition = "text[]")
    private List<String> defenses = new ArrayList<>();

    /** No-args constructor required by JPA. */
    public DamageType() {
    }

    /** Returns the damage type's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Returns the display name (e.g. "Piercing"). */
    public String getName() {
        return name;
    }

    /** Returns the database-generated lowercase/trimmed name used for matching. */
    public String getNameKey() {
        return nameKey;
    }

    /** Returns what happens when this damage type's effect is applied. */
    public String getEffect() {
        return effect;
    }

    /** Returns the skills a defender can use against this damage type. */
    public List<String> getDefenses() {
        return defenses;
    }
}
