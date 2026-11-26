package com.journey.api_rest_v1.domain.characters;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code character_powers} table: a power a character has, its level and cost,
 * and the character's own edits of the compendium power.
 */
@Entity
@Table(name = "character_powers")
public class CharacterPower {

    @EmbeddedId
    @AttributeOverride(name = "childId", column = @Column(name = "power_id", nullable = false, columnDefinition = "uuid"))
    private CharacterChildId id;

    @Column(name = "lvl", nullable = false)
    private Integer lvl = 1;

    @Column(name = "exp_spent", nullable = false)
    private Integer expSpent = 0;

    @Column(name = "notes", nullable = false, columnDefinition = "TEXT")
    private String notes = "";

    /** Character's version of the compendium fields below: null means "use the compendium value". */
    @Column(name = "name", columnDefinition = "TEXT")
    private String name;

    /** Destruction, Support, Sabotage, or Utility (checked by the database). */
    @Column(name = "type", columnDefinition = "TEXT")
    private String type;

    @Column(name = "tp_cost")
    private Integer tpCost;

    @Column(name = "hp_cost")
    private Integer hpCost;

    @Column(name = "mp_cost")
    private Integer mpCost;

    @Column(name = "ep_cost")
    private Integer epCost;

    @Column(name = "material_components", columnDefinition = "TEXT")
    private String materialComponents;

    @Column(name = "verbal")
    private Boolean verbal;

    @Column(name = "sight")
    private Boolean sight;

    @Column(name = "somatic")
    private Boolean somatic;

    @Column(name = "is_distinct")
    private Boolean isDistinct;

    @Column(name = "concentration")
    private Boolean concentration;

    @Column(name = "range", columnDefinition = "TEXT")
    private String range;

    @Column(name = "area", columnDefinition = "TEXT")
    private String area;

    @Column(name = "duration", columnDefinition = "TEXT")
    private String duration;

    @Column(name = "effect", columnDefinition = "TEXT")
    private String effect;

    @Column(name = "empower_effect", columnDefinition = "TEXT")
    private String empowerEffect;

    @Column(name = "lvl_up_effect", columnDefinition = "TEXT")
    private String lvlUpEffect;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public CharacterPower() {
    }

    public CharacterChildId getId() {
        return id;
    }

    public void setId(CharacterChildId id) {
        this.id = id;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getTpCost() {
        return tpCost;
    }

    public void setTpCost(Integer tpCost) {
        this.tpCost = tpCost;
    }

    public Integer getHpCost() {
        return hpCost;
    }

    public void setHpCost(Integer hpCost) {
        this.hpCost = hpCost;
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

    public String getMaterialComponents() {
        return materialComponents;
    }

    public void setMaterialComponents(String materialComponents) {
        this.materialComponents = materialComponents;
    }

    public Boolean getVerbal() {
        return verbal;
    }

    public void setVerbal(Boolean verbal) {
        this.verbal = verbal;
    }

    public Boolean getSight() {
        return sight;
    }

    public void setSight(Boolean sight) {
        this.sight = sight;
    }

    public Boolean getSomatic() {
        return somatic;
    }

    public void setSomatic(Boolean somatic) {
        this.somatic = somatic;
    }

    public Boolean getIsDistinct() {
        return isDistinct;
    }

    public void setIsDistinct(Boolean isDistinct) {
        this.isDistinct = isDistinct;
    }

    public Boolean getConcentration() {
        return concentration;
    }

    public void setConcentration(Boolean concentration) {
        this.concentration = concentration;
    }

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public String getEmpowerEffect() {
        return empowerEffect;
    }

    public void setEmpowerEffect(String empowerEffect) {
        this.empowerEffect = empowerEffect;
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
