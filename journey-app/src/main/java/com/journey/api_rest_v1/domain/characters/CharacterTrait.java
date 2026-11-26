package com.journey.api_rest_v1.domain.characters;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code character_traits} table: a trait a character has, where it came from
 * (racial / job / purchased), what it cost, and the character's own edits of the compendium trait.
 */
@Entity
@Table(name = "character_traits")
public class CharacterTrait {

    @EmbeddedId
    @AttributeOverride(name = "childId", column = @Column(name = "trait_id", nullable = false, columnDefinition = "uuid"))
    private CharacterChildId id;

    @Convert(converter = CharacterTraitSourceConverter.class)
    @Column(name = "source", nullable = false)
    private CharacterTraitSource source;

    @Column(name = "race_id", columnDefinition = "uuid")
    private UUID raceId;

    @Column(name = "job_id", columnDefinition = "uuid")
    private UUID jobId;

    @Column(name = "lvl", nullable = false)
    private Integer lvl = 1;

    @Column(name = "exp_spent", nullable = false)
    private Integer expSpent = 0;

    @Column(name = "job_points_spent", nullable = false)
    private Integer jobPointsSpent = 0;

    @Column(name = "notes", nullable = false, columnDefinition = "TEXT")
    private String notes = "";

    /** Character's version of the compendium fields below: null means "use the compendium value". */
    @Column(name = "trait_name", columnDefinition = "TEXT")
    private String traitName;

    @Column(name = "purchase_cost")
    private Integer purchaseCost;

    @Column(name = "lvl_up_cost")
    private Integer lvlUpCost;

    @Column(name = "trait_desc", columnDefinition = "TEXT")
    private String traitDesc;

    @Column(name = "trait_uses")
    private Integer traitUses;

    @Column(name = "mp_cost")
    private Integer mpCost;

    @Column(name = "ep_cost")
    private Integer epCost;

    @Column(name = "tp_cost")
    private Integer tpCost;

    @Column(name = "trait_effect", columnDefinition = "TEXT")
    private String traitEffect;

    @Column(name = "lvl_up_effect", columnDefinition = "TEXT")
    private String lvlUpEffect;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public CharacterTrait() {
    }

    public CharacterChildId getId() {
        return id;
    }

    public void setId(CharacterChildId id) {
        this.id = id;
    }

    public CharacterTraitSource getSource() {
        return source;
    }

    public void setSource(CharacterTraitSource source) {
        this.source = source;
    }

    public UUID getRaceId() {
        return raceId;
    }

    public void setRaceId(UUID raceId) {
        this.raceId = raceId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public Integer getLvl() {
        return lvl;
    }

    public void setLvl(Integer lvl) {
        this.lvl = lvl;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getTraitName() {
        return traitName;
    }

    public void setTraitName(String traitName) {
        this.traitName = traitName;
    }

    public Integer getPurchaseCost() {
        return purchaseCost;
    }

    public void setPurchaseCost(Integer purchaseCost) {
        this.purchaseCost = purchaseCost;
    }

    public Integer getLvlUpCost() {
        return lvlUpCost;
    }

    public void setLvlUpCost(Integer lvlUpCost) {
        this.lvlUpCost = lvlUpCost;
    }

    public String getTraitDesc() {
        return traitDesc;
    }

    public void setTraitDesc(String traitDesc) {
        this.traitDesc = traitDesc;
    }

    public Integer getTraitUses() {
        return traitUses;
    }

    public void setTraitUses(Integer traitUses) {
        this.traitUses = traitUses;
    }

    public Integer getMpCost() {
        return mpCost;
    }

    public void setMpCost(Integer mpCost) {
        this.mpCost = mpCost;
    }

    public Integer getEpCost() {
        return epCost;
    }

    public void setEpCost(Integer epCost) {
        this.epCost = epCost;
    }

    public Integer getTpCost() {
        return tpCost;
    }

    public void setTpCost(Integer tpCost) {
        this.tpCost = tpCost;
    }

    public String getTraitEffect() {
        return traitEffect;
    }

    public void setTraitEffect(String traitEffect) {
        this.traitEffect = traitEffect;
    }

    public String getLvlUpEffect() {
        return lvlUpEffect;
    }

    public void setLvlUpEffect(String lvlUpEffect) {
        this.lvlUpEffect = lvlUpEffect;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
