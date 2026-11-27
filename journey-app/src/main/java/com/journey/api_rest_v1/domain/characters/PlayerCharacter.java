package com.journey.api_rest_v1.domain.characters;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.journey.api_rest_v1.domain.races.RaceSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code characters} table (named PlayerCharacter to avoid java.lang.Character).
 * The character's own fields; races, attributes, skills, traits, powers, and inventory are separate tables.
 */
@Entity
@Table(name = "characters")
public class PlayerCharacter {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    /** The campaign this is a campaign version for (null for the player's own characters). Set by the database. */
    @Column(name = "campaign_id", insertable = false, updatable = false, columnDefinition = "uuid")
    private UUID campaignId;

    /** The character this campaign version was copied from. Set by the database. */
    @Column(name = "origin_character_id", insertable = false, updatable = false, columnDefinition = "uuid")
    private UUID originCharacterId;

    @Convert(converter = CharacterStatusConverter.class)
    @Column(name = "status", nullable = false)
    private CharacterStatus status = CharacterStatus.DRAFT;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description = "";

    @Column(name = "motive", nullable = false, columnDefinition = "TEXT")
    private String motive = "";

    @Column(name = "ideals", nullable = false, columnDefinition = "TEXT")
    private String ideals = "";

    @Column(name = "strengths", nullable = false, columnDefinition = "TEXT")
    private String strengths = "";

    @Column(name = "flaws", nullable = false, columnDefinition = "TEXT")
    private String flaws = "";

    @Column(name = "learned_languages", nullable = false, columnDefinition = "TEXT")
    private String learnedLanguages = "";

    @Column(name = "history", nullable = false, columnDefinition = "TEXT")
    private String history = "";

    @Column(name = "important_characters", nullable = false, columnDefinition = "TEXT")
    private String importantCharacters = "";

    @Column(name = "notes", nullable = false, columnDefinition = "TEXT")
    private String notes = "";

    @Column(name = "character_sheet_id", columnDefinition = "uuid")
    private UUID characterSheetId;

    @Column(name = "job_id", columnDefinition = "uuid")
    private UUID jobId;

    /** Stored in the Postgres "size" enum (labels match the constants). */
    @Enumerated(EnumType.STRING)
    @Column(name = "size")
    private RaceSize size;

    @Column(name = "language_race_id", columnDefinition = "uuid")
    private UUID languageRaceId;

    @Column(name = "starting_exp", nullable = false)
    private Integer startingExp = 150;

    @Column(name = "job_points", nullable = false)
    private Integer jobPoints = 50;

    @Column(name = "current_hp")
    private Integer currentHp;

    @Column(name = "current_wp")
    private Integer currentWp;

    @Column(name = "current_ep")
    private Integer currentEp;

    @Column(name = "current_mp")
    private Integer currentMp;

    @Column(name = "temp_hp", nullable = false)
    private Integer tempHp = 0;

    @Column(name = "hp_bonus", nullable = false)
    private Integer hpBonus = 0;

    @Column(name = "wp_bonus", nullable = false)
    private Integer wpBonus = 0;

    @Column(name = "ep_bonus", nullable = false)
    private Integer epBonus = 0;

    @Column(name = "mp_bonus", nullable = false)
    private Integer mpBonus = 0;

    @Column(name = "physical_damage_bonus", nullable = false)
    private Integer physicalDamageBonus = 0;

    @Column(name = "magic_damage_bonus", nullable = false)
    private Integer magicDamageBonus = 0;

    @Column(name = "priority_bonus", nullable = false)
    private Integer priorityBonus = 0;

    @Column(name = "movement_bonus", nullable = false)
    private Integer movementBonus = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public PlayerCharacter() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public CharacterStatus getStatus() {
        return status;
    }

    public void setStatus(CharacterStatus status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMotive() {
        return motive;
    }

    public void setMotive(String motive) {
        this.motive = motive;
    }

    public String getIdeals() {
        return ideals;
    }

    public void setIdeals(String ideals) {
        this.ideals = ideals;
    }

    public String getStrengths() {
        return strengths;
    }

    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }

    public String getFlaws() {
        return flaws;
    }

    public void setFlaws(String flaws) {
        this.flaws = flaws;
    }

    public String getLearnedLanguages() {
        return learnedLanguages;
    }

    public void setLearnedLanguages(String learnedLanguages) {
        this.learnedLanguages = learnedLanguages;
    }

    public String getHistory() {
        return history;
    }

    public void setHistory(String history) {
        this.history = history;
    }

    public String getImportantCharacters() {
        return importantCharacters;
    }

    public void setImportantCharacters(String importantCharacters) {
        this.importantCharacters = importantCharacters;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getCharacterSheetId() {
        return characterSheetId;
    }

    public void setCharacterSheetId(UUID characterSheetId) {
        this.characterSheetId = characterSheetId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public RaceSize getSize() {
        return size;
    }

    public void setSize(RaceSize size) {
        this.size = size;
    }

    public UUID getLanguageRaceId() {
        return languageRaceId;
    }

    public void setLanguageRaceId(UUID languageRaceId) {
        this.languageRaceId = languageRaceId;
    }

    public Integer getStartingExp() {
        return startingExp;
    }

    public void setStartingExp(Integer startingExp) {
        this.startingExp = startingExp;
    }

    public Integer getJobPoints() {
        return jobPoints;
    }

    public void setJobPoints(Integer jobPoints) {
        this.jobPoints = jobPoints;
    }

    public Integer getCurrentHp() {
        return currentHp;
    }

    public void setCurrentHp(Integer currentHp) {
        this.currentHp = currentHp;
    }

    public Integer getCurrentWp() {
        return currentWp;
    }

    public void setCurrentWp(Integer currentWp) {
        this.currentWp = currentWp;
    }

    public Integer getCurrentEp() {
        return currentEp;
    }

    public void setCurrentEp(Integer currentEp) {
        this.currentEp = currentEp;
    }

    public Integer getCurrentMp() {
        return currentMp;
    }

    public void setCurrentMp(Integer currentMp) {
        this.currentMp = currentMp;
    }

    public Integer getTempHp() {
        return tempHp;
    }

    public void setTempHp(Integer tempHp) {
        this.tempHp = tempHp;
    }

    public Integer getHpBonus() {
        return hpBonus;
    }

    public void setHpBonus(Integer hpBonus) {
        this.hpBonus = hpBonus;
    }

    public Integer getWpBonus() {
        return wpBonus;
    }

    public void setWpBonus(Integer wpBonus) {
        this.wpBonus = wpBonus;
    }

    public Integer getEpBonus() {
        return epBonus;
    }

    public void setEpBonus(Integer epBonus) {
        this.epBonus = epBonus;
    }

    public Integer getMpBonus() {
        return mpBonus;
    }

    public void setMpBonus(Integer mpBonus) {
        this.mpBonus = mpBonus;
    }

    public Integer getPhysicalDamageBonus() {
        return physicalDamageBonus;
    }

    public void setPhysicalDamageBonus(Integer physicalDamageBonus) {
        this.physicalDamageBonus = physicalDamageBonus;
    }

    public Integer getMagicDamageBonus() {
        return magicDamageBonus;
    }

    public void setMagicDamageBonus(Integer magicDamageBonus) {
        this.magicDamageBonus = magicDamageBonus;
    }

    public Integer getPriorityBonus() {
        return priorityBonus;
    }

    public void setPriorityBonus(Integer priorityBonus) {
        this.priorityBonus = priorityBonus;
    }

    public Integer getMovementBonus() {
        return movementBonus;
    }

    public void setMovementBonus(Integer movementBonus) {
        this.movementBonus = movementBonus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public UUID getOriginCharacterId() {
        return originCharacterId;
    }
}
