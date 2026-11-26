package com.journey.api_rest_v1.domain.characters;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Composite key (character_id, child id) shared by the character's races, skills, traits, and powers.
 * Each entity renames {@code childId} to its column (race_id, skill_id, ...) with {@code @AttributeOverride}.
 */
@Embeddable
public class CharacterChildId implements Serializable {

    @Column(name = "character_id", nullable = false, columnDefinition = "uuid")
    private UUID characterId;

    private UUID childId;

    /** No-args constructor required by JPA. */
    public CharacterChildId() {
    }

    /** Creates a key for a character and one of its races, skills, traits, or powers. */
    public CharacterChildId(UUID characterId, UUID childId) {
        this.characterId = characterId;
        this.childId = childId;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public UUID getChildId() {
        return childId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CharacterChildId that = (CharacterChildId) o;
        return Objects.equals(characterId, that.characterId) && Objects.equals(childId, that.childId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, childId);
    }
}
