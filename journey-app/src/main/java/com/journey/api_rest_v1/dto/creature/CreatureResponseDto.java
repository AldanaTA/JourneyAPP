package com.journey.api_rest_v1.dto.creature;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.journey.api_rest_v1.domain.creatures.AbilityTiming;
import com.journey.api_rest_v1.domain.creatures.AugmentKind;
import com.journey.api_rest_v1.domain.races.RaceSize;
import com.journey.api_rest_v1.dto.item.DamageTypeDto;
import com.journey.api_rest_v1.dto.item.StatusConditionDto;
import com.journey.api_rest_v1.dto.item.WeaponPropertyDto;
import com.journey.api_rest_v1.dto.power.PowerResponseDto;
import com.journey.api_rest_v1.dto.trait.TraitResponseDto;

/**
 * Creature payload returned by the creature endpoints. Abilities linked to a game trait or power embed
 * the full trait/power so clients can show it without another request.
 * It is Serializable so it can be stored in the Redis cache.
 */
public record CreatureResponseDto(
    String id,
    String name,
    String creatureType,
    Integer threatLvl,
    RaceSize minSize,
    RaceSize maxSize,
    String description,
    String languages,
    String lore,
    Integer hp,
    Integer wp,
    Integer mp,
    Integer ep,
    Integer prio,
    Integer movement,
    Integer pd,
    Integer md,
    List<Skill> skills,
    List<Augment> augments,
    List<Ability> abilities,
    List<Attack> attacks,
    String contentSourceName
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** A listed skill rating. */
    public record Skill(String skill, Integer rating) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** An immunity, resistance, or weakness; exactly one of damageType / statusCondition is set. */
    public record Augment(AugmentKind kind, String target, DamageTypeDto damageType,
            StatusConditionDto statusCondition) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** An ability with the creature's own text and, when linked, the full trait or power. */
    public record Ability(
        AbilityTiming timing,
        String name,
        String effect,
        Integer lvl,
        Integer uses,
        Integer mpCost,
        Integer epCost,
        Integer tpCost,
        TraitResponseDto trait,
        PowerResponseDto power
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** A declared attack. */
    public record Attack(
        String name,
        String skill,
        Integer rangeFt,
        String damageDice,
        String damageBonus,
        DamageTypeDto damageType,
        Integer mag,
        List<WeaponPropertyDto> properties
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
