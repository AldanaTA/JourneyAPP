package com.journey.api_rest_v1.domain.characters;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code character_inventories} table: a character's money and attunement limit.
 */
@Entity
@Table(name = "character_inventories")
public class CharacterInventory {

    @Id
    @Column(name = "character_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID characterId;

    @Column(name = "platinum", nullable = false)
    private Integer platinum = 0;

    @Column(name = "gold", nullable = false)
    private Integer gold = 0;

    @Column(name = "silver", nullable = false)
    private Integer silver = 0;

    @Column(name = "bronze", nullable = false)
    private Integer bronze = 0;

    @Column(name = "attunement_limit", nullable = false)
    private Integer attunementLimit = 3;

    /** Multiplies every item's stack limit (2 for Supply Sergeant). */
    @Column(name = "stack_limit_multiplier", nullable = false)
    private Integer stackLimitMultiplier = 1;

    @Column(name = "notes", nullable = false, columnDefinition = "TEXT")
    private String notes = "";

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public CharacterInventory() {
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public void setCharacterId(UUID characterId) {
        this.characterId = characterId;
    }

    public Integer getPlatinum() {
        return platinum;
    }

    public void setPlatinum(Integer platinum) {
        this.platinum = platinum;
    }

    public Integer getGold() {
        return gold;
    }

    public void setGold(Integer gold) {
        this.gold = gold;
    }

    public Integer getSilver() {
        return silver;
    }

    public void setSilver(Integer silver) {
        this.silver = silver;
    }

    public Integer getBronze() {
        return bronze;
    }

    public void setBronze(Integer bronze) {
        this.bronze = bronze;
    }

    public Integer getAttunementLimit() {
        return attunementLimit;
    }

    public void setAttunementLimit(Integer attunementLimit) {
        this.attunementLimit = attunementLimit;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Integer getStackLimitMultiplier() {
        return stackLimitMultiplier;
    }

    public void setStackLimitMultiplier(Integer stackLimitMultiplier) {
        this.stackLimitMultiplier = stackLimitMultiplier;
    }
}
