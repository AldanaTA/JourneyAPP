package com.journey.api_rest_v1.domain.powers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.journey.api_rest_v1.domain.content_source.ContentSource;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "powers")
public class Power {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_key", insertable = false, updatable = false)
    private String nameKey;

    @Column(name = "lvl", nullable = false)
    private Integer lvl;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PowerType type;

    @Column(name = "tp_cost", nullable = false)
    private Integer tpCost;

    @Column(name = "hp_cost")
    private Integer hpCost;

    @Column(name = "mp_cost")
    private Integer mpCost;

    @Column(name = "ep_cost")
    private Integer epCost;

    @Column(name = "material_components")
    private String materialComponents;

    @Column(name = "verbal", nullable = false)
    private Boolean verbal;

    @Column(name = "sight", nullable = false)
    private Boolean sight;

    @Column(name = "somatic", nullable = false)
    private Boolean somatic;

    @Column(name = "is_distinct", nullable = false)
    private Boolean isDistinct;

    @Column(name = "concentration", nullable = false)
    private Boolean concentration;

    @Column(name = "range")
    private String range;

    @Column(name = "area")
    private String area;

    @Column(name = "duration")
    private String duration;

    @Column(name = "effect", nullable = false, columnDefinition = "TEXT")
    private String effect;

    @Column(name = "empower_effect")
    private String empowerEffect;

    @Column(name = "lvl_up_effect")
    private String lvlUpEffect;

    @OneToMany(mappedBy = "power", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PowerCategories> categories = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "source_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_powers_content_source")
    )
    private ContentSource source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Power() {
    }

    public Power(String id, String name, Integer lvl, PowerType type, Integer tpCost, Integer hpCost,
            Integer mpCost, Integer epCost, String materialComponents, Boolean verbal, Boolean sight, Boolean somatic,
            Boolean isDistinct, Boolean concentration, String range, String area, String duration, String effect,
            String empowerEffect, String lvlUpEffect, List<PowerCategories> categories, ContentSource source,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.lvl = lvl;
        this.type = type;
        this.tpCost = tpCost;
        this.hpCost = hpCost;
        this.mpCost = mpCost;
        this.epCost = epCost;
        this.materialComponents = materialComponents;
        this.verbal = verbal;
        this.sight = sight;
        this.somatic = somatic;
        this.isDistinct = isDistinct;
        this.concentration = concentration;
        this.range = range;
        this.area = area;
        this.duration = duration;
        this.effect = effect;
        this.empowerEffect = empowerEffect;
        this.lvlUpEffect = lvlUpEffect;
        this.categories = categories == null ? new ArrayList<>() : categories;
        this.source = source;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public Integer getLvl() {
        return lvl;
    }

    public void setLvl(Integer lvl) {
        this.lvl = lvl;
    }

    public PowerType getType() {
        return type;
    }

    public void setType(PowerType type) {
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

    public List<PowerCategories> getCategories() {
        return categories;
    }

    public void setCategories(List<PowerCategories> categories) {
        this.categories = categories == null ? new ArrayList<>() : categories;
    }

    public ContentSource getSource() {
        return source;
    }

    public void setSource(ContentSource source) {
        this.source = source;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

