package com.journey.api_rest_v1.domain.characters;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

/** Composite key (character_id, attribute) of {@code character_attributes}. */
@Embeddable
public class CharacterAttributeId implements Serializable {

    @Column(name = "character_id", nullable = false, columnDefinition = "uuid")
    private UUID characterId;

    @Convert(converter = CharacterAttributeNameConverter.class)
    @Column(name = "attribute", nullable = false)
    private CharacterAttributeName attribute;

    /** No-args constructor required by JPA. */
    public CharacterAttributeId() {
    }

    /** Creates a key for one attribute of a character. */
    public CharacterAttributeId(UUID characterId, CharacterAttributeName attribute) {
        this.characterId = characterId;
        this.attribute = attribute;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public CharacterAttributeName getAttribute() {
        return attribute;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CharacterAttributeId that = (CharacterAttributeId) o;
        return Objects.equals(characterId, that.characterId) && attribute == that.attribute;
    }

    @Override
    public int hashCode() {
        return Objects.hash(characterId, attribute);
    }
}
