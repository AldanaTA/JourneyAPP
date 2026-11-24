package com.journey.api_rest_v1.domain.items;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code status_conditions} reference table (Blinded, Prone, ...).
 * Core rules like {@link DamageType}; seeded by the schema and read-only from the API's point of view.
 */
@Entity
@Table(name = "status_conditions")
public class StatusCondition {

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
    public StatusCondition() {
    }

    /** Returns the condition's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Returns the display name (e.g. "Blinded"). */
    public String getName() {
        return name;
    }

    /** Returns the database-generated lowercase/trimmed name used for matching. */
    public String getNameKey() {
        return nameKey;
    }

    /** Returns the rules text; paragraphs are separated by blank lines. */
    public String getDescription() {
        return description;
    }
}
