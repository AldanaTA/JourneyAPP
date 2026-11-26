package com.journey.api_rest_v1.domain.characters;

import java.time.OffsetDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code character_skills} table: one skill of a character.
 * Rating = skill base + creationAdjustment + expSpent + jobPointsSpent + permanentModifier + temporaryModifier (min 5).
 */
@Entity
@Table(name = "character_skills")
public class CharacterSkill {

    @EmbeddedId
    @AttributeOverride(name = "childId", column = @Column(name = "skill_id", nullable = false, columnDefinition = "uuid"))
    private CharacterChildId id;

    @Column(name = "creation_adjustment", nullable = false)
    private Integer creationAdjustment = 0;

    @Column(name = "exp_spent", nullable = false)
    private Integer expSpent = 0;

    @Column(name = "job_points_spent", nullable = false)
    private Integer jobPointsSpent = 0;

    @Column(name = "failures", nullable = false)
    private Integer failures = 0;

    @Column(name = "permanent_modifier", nullable = false)
    private Integer permanentModifier = 0;

    @Column(name = "temporary_modifier", nullable = false)
    private Integer temporaryModifier = 0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public CharacterSkill() {
    }

    public CharacterChildId getId() {
        return id;
    }

    public void setId(CharacterChildId id) {
        this.id = id;
    }

    public Integer getCreationAdjustment() {
        return creationAdjustment;
    }

    public void setCreationAdjustment(Integer creationAdjustment) {
        this.creationAdjustment = creationAdjustment;
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

    public Integer getFailures() {
        return failures;
    }

    public void setFailures(Integer failures) {
        this.failures = failures;
    }

    public Integer getPermanentModifier() {
        return permanentModifier;
    }

    public void setPermanentModifier(Integer permanentModifier) {
        this.permanentModifier = permanentModifier;
    }

    public Integer getTemporaryModifier() {
        return temporaryModifier;
    }

    public void setTemporaryModifier(Integer temporaryModifier) {
        this.temporaryModifier = temporaryModifier;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
