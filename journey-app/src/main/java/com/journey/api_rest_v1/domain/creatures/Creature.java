package com.journey.api_rest_v1.domain.creatures;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import com.journey.api_rest_v1.domain.content_source.ContentSource;
import com.journey.api_rest_v1.domain.races.RaceSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code creatures} table, following the creature stat block from the
 * Storyteller Tools: header, lore, combat stats, skills, augments, abilities, and attacks.
 * Threat points are only used to build a creature and are not stored.
 */
@Entity
@Table(name = "creatures")
public class Creature {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private ContentSource source;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_key", insertable = false, updatable = false)
    private String nameKey;

    // Must belong to the creature's own source (enforced by a composite foreign key in the database).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creature_type_id", nullable = false)
    private CreatureType creatureType;

    @Column(name = "threat_lvl", nullable = false)
    private Integer threatLvl;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_size", nullable = false)
    private RaceSize minSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "max_size")
    private RaceSize maxSize;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "languages", columnDefinition = "TEXT")
    private String languages;

    @Column(name = "lore", columnDefinition = "TEXT")
    private String lore;

    @Column(name = "hp", nullable = false)
    private Integer hp;

    @Column(name = "wp")
    private Integer wp;

    @Column(name = "mp", nullable = false)
    private Integer mp;

    @Column(name = "ep", nullable = false)
    private Integer ep;

    @Column(name = "prio", nullable = false)
    private Integer prio;

    @Column(name = "movement", nullable = false)
    private Integer movement;

    @Column(name = "pd", nullable = false)
    private Integer pd;

    @Column(name = "md", nullable = false)
    private Integer md;

    // [{"skill": "Brawl", "rating": 140}, ...]; unlisted skills are 40%.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> skills = new ArrayList<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "creature", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CreatureAugment> augments = new ArrayList<>();

    @BatchSize(size = 50)
    @OrderBy("sortOrder ASC")
    @OneToMany(mappedBy = "creature", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CreatureAbility> abilities = new ArrayList<>();

    @BatchSize(size = 50)
    @OrderBy("sortOrder ASC")
    @OneToMany(mappedBy = "creature", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CreatureAttack> attacks = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public Creature() {
    }

    /** Returns the creature's UUID primary key. */
    public UUID getId() { return id; }

    /** Returns the content source this creature belongs to. */
    public ContentSource getSource() { return source; }

    /** Sets the content source this creature belongs to. */
    public void setSource(ContentSource source) { this.source = source; }

    /** Returns the display name. */
    public String getName() { return name; }

    /** Sets the display name. */
    public void setName(String name) { this.name = name; }

    /** Returns the database-generated lowercase/trimmed name used for uniqueness. */
    public String getNameKey() { return nameKey; }

    /** Returns the creature type (from the creature's own source). */
    public CreatureType getCreatureType() { return creatureType; }

    /** Sets the creature type. */
    public void setCreatureType(CreatureType creatureType) { this.creatureType = creatureType; }

    /** Returns the threat level. */
    public Integer getThreatLvl() { return threatLvl; }

    /** Sets the threat level. */
    public void setThreatLvl(Integer threatLvl) { this.threatLvl = threatLvl; }

    /** Returns the size, or the smallest size of its range. */
    public RaceSize getMinSize() { return minSize; }

    /** Sets the size, or the smallest size of its range. */
    public void setMinSize(RaceSize minSize) { this.minSize = minSize; }

    /** Returns the largest size of the range, or null for a single size. */
    public RaceSize getMaxSize() { return maxSize; }

    /** Sets the largest size of the range (null for a single size). */
    public void setMaxSize(RaceSize maxSize) { this.maxSize = maxSize; }

    /** Returns the description. */
    public String getDescription() { return description; }

    /** Sets the description. */
    public void setDescription(String description) { this.description = description; }

    /** Returns the languages the creature speaks. */
    public String getLanguages() { return languages; }

    /** Sets the languages the creature speaks. */
    public void setLanguages(String languages) { this.languages = languages; }

    /** Returns the lore and behaviors. */
    public String getLore() { return lore; }

    /** Sets the lore and behaviors. */
    public void setLore(String lore) { this.lore = lore; }

    /** Returns HP. */
    public Integer getHp() { return hp; }

    /** Sets HP. */
    public void setHp(Integer hp) { this.hp = hp; }

    /** Returns WP, or null when the creature has none. */
    public Integer getWp() { return wp; }

    /** Sets WP. */
    public void setWp(Integer wp) { this.wp = wp; }

    /** Returns MP. */
    public Integer getMp() { return mp; }

    /** Sets MP. */
    public void setMp(Integer mp) { this.mp = mp; }

    /** Returns EP. */
    public Integer getEp() { return ep; }

    /** Sets EP. */
    public void setEp(Integer ep) { this.ep = ep; }

    /** Returns PRIO. */
    public Integer getPrio() { return prio; }

    /** Sets PRIO. */
    public void setPrio(Integer prio) { this.prio = prio; }

    /** Returns movement (MVM) in feet. */
    public Integer getMovement() { return movement; }

    /** Sets movement (MVM) in feet. */
    public void setMovement(Integer movement) { this.movement = movement; }

    /** Returns physical damage (PD). */
    public Integer getPd() { return pd; }

    /** Sets physical damage (PD). */
    public void setPd(Integer pd) { this.pd = pd; }

    /** Returns magical damage (MD). */
    public Integer getMd() { return md; }

    /** Sets magical damage (MD). */
    public void setMd(Integer md) { this.md = md; }

    /** Returns the listed skill ratings. */
    public List<Map<String, Object>> getSkills() { return skills; }

    /** Replaces the listed skill ratings; null becomes empty. */
    public void setSkills(List<Map<String, Object>> skills) { this.skills = skills == null ? new ArrayList<>() : skills; }

    /** Returns the immunities, resistances, and weaknesses. */
    public List<CreatureAugment> getAugments() { return augments; }

    /** Returns the abilities in display order. */
    public List<CreatureAbility> getAbilities() { return abilities; }

    /** Returns the attacks in display order. */
    public List<CreatureAttack> getAttacks() { return attacks; }

    /** Returns when the creature was created. */
    public OffsetDateTime getCreatedAt() { return createdAt; }

    /** Returns when the creature was last updated. */
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
