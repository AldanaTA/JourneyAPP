package com.journey.api_rest_v1.dto.creature;

import java.util.List;

import com.journey.api_rest_v1.domain.creatures.AbilityTiming;
import com.journey.api_rest_v1.domain.creatures.AugmentKind;
import com.journey.api_rest_v1.domain.races.RaceSize;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body used to create or fully update a creature (snake_case like the other create requests).
 *
 * <p>{@code creature_type} is a type name from the creature's own content source. Each augment target must
 * name a damage type or a status condition. Abilities may link
 * to a game trait ({@code trait_id}) or power ({@code power_id}), not both. Attack damage types and
 * properties are reference names (e.g. "Slashing", "Finesse").</p>
 */
public record CreateCreatureRequest(
    @NotBlank(message = "Name" + CreateCreatureRequest.ERROR_MESSAGE_NOT_BLANK) String name,
    @NotBlank(message = "Creature type" + CreateCreatureRequest.ERROR_MESSAGE_NOT_BLANK) String creature_type,
    @NotNull(message = "Threat LVL" + CreateCreatureRequest.ERROR_MESSAGE_NOT_BLANK) @Min(1) Integer threat_lvl,
    @NotNull(message = "Min size" + CreateCreatureRequest.ERROR_MESSAGE_NOT_BLANK) RaceSize min_size,
    RaceSize max_size,
    String description,
    String languages,
    String lore,
    @NotNull(message = "HP" + CreateCreatureRequest.ERROR_MESSAGE_NOT_BLANK) @Min(0) Integer hp,
    @Min(0) Integer wp,
    @Min(0) Integer mp,
    @Min(0) Integer ep,
    Integer prio,
    @Min(0) Integer movement,
    Integer pd,
    Integer md,
    List<@Valid Skill> skills,
    List<@Valid Augment> augments,
    List<@Valid Ability> abilities,
    List<@Valid Attack> attacks,
    @NotBlank(message = "Content source id" + CreateCreatureRequest.ERROR_MESSAGE_NOT_BLANK) String content_source_id
) {
    static final String ERROR_MESSAGE_NOT_BLANK = " field must not be blank";

    /** A listed skill rating, e.g. Brawl 140. */
    public record Skill(@NotBlank String skill, @NotNull @Min(0) Integer rating) {
    }

    /** An immunity, resistance, or weakness. */
    public record Augment(@NotNull AugmentKind kind, @NotBlank String target) {
    }

    /** A passive, triggered, or declared ability (attacks go in {@code attacks}). */
    public record Ability(
        @NotNull AbilityTiming timing,
        @NotBlank String name,
        @NotBlank String effect,
        String trait_id,
        String power_id,
        @Min(1) Integer lvl,
        @Min(0) Integer uses,
        @Min(0) Integer mp_cost,
        @Min(0) Integer ep_cost,
        @Min(0) Integer tp_cost
    ) {
    }

    /** A declared attack. */
    public record Attack(
        @NotBlank String name,
        @NotBlank String skill,
        @Min(0) Integer range_ft,
        @NotBlank @Pattern(regexp = "(?i)\\d+d\\d+", message = "Damage dice must look like 2d6") String damage_dice,
        String damage_bonus,
        @NotBlank String damage_type,
        @Min(1) Integer mag,
        List<String> properties
    ) {
    }
}
