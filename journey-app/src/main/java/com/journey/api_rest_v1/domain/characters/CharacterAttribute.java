package com.journey.api_rest_v1.domain.characters;

import java.time.OffsetDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code character_attributes} table: what one attribute got from EXP and
 * job points, plus bonuses. Value = sheet base + expSpent + jobPointsSpent + bonus + modifier.
 */
@Entity
@Table(name = "character_attributes")
public class CharacterAttribute {

    @EmbeddedId
    private CharacterAttributeId id;

    @Column(name = "exp_spent", nullable = false)
    private Integer expSpent = 0;

    @Column(name = "job_points_spent", nullable = false)
    private Integer jobPointsSpent = 0;

    @Column(name = "bonus", nullable = false)
    private Integer bonus = 0;

    @Column(name = "modifier", nullable = false)
    private Integer modifier = 0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public CharacterAttribute() {
    }

    public CharacterAttributeId getId() {
        return id;
    }

    public void setId(CharacterAttributeId id) {
        this.id = id;
    }

    public Integer getExpSpent() {
        return expSpent;
    }

    public void setExpSpent(Integer expSpent) {
        this.expSpent = expSpent;
    }

    public Integer getJobPointsSpent() {
        return jobPointsSpent;
    }

    public void setJobPointsSpent(Integer jobPointsSpent) {
        this.jobPointsSpent = jobPointsSpent;
    }

    public Integer getBonus() {
        return bonus;
    }

    public void setBonus(Integer bonus) {
        this.bonus = bonus;
    }

    public Integer getModifier() {
        return modifier;
    }

    public void setModifier(Integer modifier) {
        this.modifier = modifier;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
