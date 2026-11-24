package com.journey.api_rest_v1.domain.items;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code weapon_properties} reference table (e.g. Finesse, Two-Hand).
 * Seeded by the schema; read-only from the API's point of view.
 */
@Entity
@Table(name = "weapon_properties")
public class WeaponProperty {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_key", insertable = false, updatable = false)
    private String nameKey;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /** No-args constructor required by JPA. */
    public WeaponProperty() {
    }

    /** Returns the property's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Returns the display name (e.g. "Two-Hand"). */
    public String getName() {
        return name;
    }

    /** Returns the database-generated lowercase/trimmed name used for matching. */
    public String getNameKey() {
        return nameKey;
    }

    /** Returns the rules text of the property. */
    public String getDescription() {
        return description;
    }
}
