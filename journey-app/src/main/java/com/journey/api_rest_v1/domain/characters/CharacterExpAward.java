package com.journey.api_rest_v1.domain.characters;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code character_exp_awards} table: EXP gained after creation (or a correction).
 */
@Entity
@Table(name = "character_exp_awards")
public class CharacterExpAward {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "character_id", nullable = false, columnDefinition = "uuid")
    private UUID characterId;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason = "";

    @Column(name = "awarded_by_user_id", columnDefinition = "uuid")
    private UUID awardedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** No-args constructor required by JPA. */
    public CharacterExpAward() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public void setCharacterId(UUID characterId) {
        this.characterId = characterId;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getAwardedByUserId() {
        return awardedByUserId;
    }

    public void setAwardedByUserId(UUID awardedByUserId) {
        this.awardedByUserId = awardedByUserId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
