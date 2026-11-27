package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.PlayerCharacter;

/**
 * Data access for characters, plus reads of the character views (budget and stats are computed by the
 * database, so the totals have one definition) and the race/job trait lists used to check picks.
 */
@Repository
public interface PlayerCharacterRepository extends JpaRepository<PlayerCharacter, UUID> {

    List<PlayerCharacter> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    /** The user's own characters, without their campaign versions. */
    List<PlayerCharacter> findByUserIdAndCampaignIdIsNullOrderByUpdatedAtDesc(UUID userId);

    /** True when the user is a storyteller (owner or co-storyteller) of the campaign. */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM content_sources s WHERE s.id = :campaignId AND CAST(s.kind AS TEXT) = 'campaign'
                  AND (s.owner_user_id = :userId OR EXISTS (
                      SELECT 1 FROM source_members m WHERE m.source_id = s.id AND m.user_id = :userId
                        AND CAST(m.role AS TEXT) IN ('owner', 'editor'))))
            """, nativeQuery = true)
    boolean isCampaignStoryteller(@Param("campaignId") UUID campaignId, @Param("userId") UUID userId);

    /** One row of {@code character_point_budget}. */
    interface BudgetView {
        Integer getTotalExp();
        Integer getAttributeExp();
        Integer getSkillExp();
        Integer getTraitExp();
        Integer getPowerExp();
        Integer getExpRemaining();
        Integer getJobPoints();
        Integer getJobTraitPoints();
        Integer getJobPointsOnStats();
        Integer getJobPointsRemaining();
        Integer getRaceCount();
        Integer getRacialTraitCost();
        Integer getCombatSkillAdjustment();
        Integer getLifeSkillAdjustment();
    }

    @Query(value = """
            SELECT CAST(total_exp AS int) AS "totalExp", CAST(attribute_exp AS int) AS "attributeExp",
                   CAST(skill_exp AS int) AS "skillExp", CAST(trait_exp AS int) AS "traitExp",
                   CAST(power_exp AS int) AS "powerExp", CAST(exp_remaining AS int) AS "expRemaining",
                   job_points AS "jobPoints", CAST(job_trait_points AS int) AS "jobTraitPoints",
                   CAST(job_points_on_stats AS int) AS "jobPointsOnStats",
                   CAST(job_points_remaining AS int) AS "jobPointsRemaining", CAST(race_count AS int) AS "raceCount",
                   CAST(racial_trait_cost AS int) AS "racialTraitCost",
                   CAST(combat_skill_adjustment AS int) AS "combatSkillAdjustment",
                   CAST(life_skill_adjustment AS int) AS "lifeSkillAdjustment"
            FROM character_point_budget WHERE character_id = :id
            """, nativeQuery = true)
    Optional<BudgetView> findBudget(@Param("id") UUID characterId);

    /** One row of {@code character_stats}. */
    interface StatsView {
        Integer getBody();
        Integer getStrength();
        Integer getSpeed();
        Integer getMagic();
        Integer getMaxHp();
        Integer getMaxWp();
        Integer getMaxEp();
        Integer getMaxMp();
        Integer getPhysicalDamage();
        Integer getMagicDamage();
        Integer getPriority();
        Integer getMovementFt();
    }

    @Query(value = """
            SELECT CAST(body AS int) AS "body", CAST(strength AS int) AS "strength", CAST(speed AS int) AS "speed",
                   CAST(magic AS int) AS "magic", CAST(max_hp AS int) AS "maxHp", CAST(max_wp AS int) AS "maxWp",
                   CAST(max_ep AS int) AS "maxEp", CAST(max_mp AS int) AS "maxMp",
                   CAST(physical_damage AS int) AS "physicalDamage", CAST(magic_damage AS int) AS "magicDamage",
                   CAST(priority AS int) AS "priority", CAST(movement_ft AS int) AS "movementFt"
            FROM character_stats WHERE character_id = :id
            """, nativeQuery = true)
    Optional<StatsView> findStats(@Param("id") UUID characterId);

    /** True when the race lists the trait among its racial traits. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM race_traits WHERE race_id = :raceId AND trait_id = :traitId)",
            nativeQuery = true)
    boolean raceHasTrait(@Param("raceId") UUID raceId, @Param("traitId") UUID traitId);

    /** The racial traits of a race, in display order. */
    @Query(value = "SELECT trait_id FROM race_traits WHERE race_id = :raceId ORDER BY sort_order", nativeQuery = true)
    List<UUID> findRaceTraitIds(@Param("raceId") UUID raceId);

    /** True when the job offers the trait as one of its free picks. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM job_traits WHERE job_id = :jobId AND trait_id = :traitId)",
            nativeQuery = true)
    boolean jobHasTrait(@Param("jobId") UUID jobId, @Param("traitId") UUID traitId);

    /** The content sources the character is built from, by name. */
    @Query(value = """
            SELECT ccs.source_id FROM character_content_sources ccs
            JOIN content_sources s ON s.id = ccs.source_id
            WHERE ccs.character_id = :id ORDER BY s.source_name
            """, nativeQuery = true)
    List<UUID> findSourceIds(@Param("id") UUID characterId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM character_content_sources WHERE character_id = :id", nativeQuery = true)
    void clearSources(@Param("id") UUID characterId);

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO character_content_sources (character_id, source_id) VALUES (:id, :sourceId)",
            nativeQuery = true)
    void addSource(@Param("id") UUID characterId, @Param("sourceId") UUID sourceId);

    /** Makes a just-created character a campaign character (campaign_id is not written through the entity). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE characters SET campaign_id = :campaign WHERE id = :id", nativeQuery = true)
    void setCampaign(@Param("id") UUID characterId, @Param("campaign") UUID campaignId);

    /** The character's campaign, or null. */
    @Query(value = "SELECT CAST(campaign_id AS TEXT) FROM characters WHERE id = :id", nativeQuery = true)
    String findCampaignId(@Param("id") UUID characterId);

    /** How many of these sources the campaign allows (the core rules, its listed sources, and itself). */
    @Query(value = """
            SELECT COUNT(*) FROM content_sources s WHERE s.id IN (:ids)
              AND (CAST(s.kind AS TEXT) = 'core' OR s.id = :campaign
                   OR s.id IN (SELECT source_id FROM campaign_allowed_sources WHERE campaign_id = :campaign))
            """, nativeQuery = true)
    long countCampaignSources(@Param("ids") java.util.Collection<UUID> ids, @Param("campaign") UUID campaignId);
}
