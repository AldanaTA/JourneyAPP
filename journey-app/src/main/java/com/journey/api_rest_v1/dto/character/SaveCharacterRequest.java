package com.journey.api_rest_v1.dto.character;

import java.util.List;
import java.util.Map;

import com.journey.api_rest_v1.domain.characters.CharacterAttributeName;
import com.journey.api_rest_v1.domain.characters.CharacterTraitSource;
import com.journey.api_rest_v1.domain.characters.InventoryLocation;
import com.journey.api_rest_v1.domain.races.RaceSize;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The whole character, as the editor has it, saved in one request (PUT /characters/{id}).
 *
 * <p>Every field is optional: null leaves that part of the character as it is. A list that is given replaces
 * what the character has (traits and powers not listed are removed, and so on). The changes are checked
 * together, so a save that moves EXP from one place to another never fails half way.</p>
 */
public record SaveCharacterRequest(
    @Size(max = 100) String name,
    String description,
    String motive,
    String ideals,
    String strengths,
    String flaws,
    String learned_languages,
    String history,
    String important_characters,
    String notes,

    String character_sheet_id,
    RaceSize size,
    @Min(0) Integer starting_exp,
    @Min(0) Integer job_points,

    /** Content sources besides the core rules. */
    List<String> source_ids,
    /** The races in order (several for a mixed race); an empty list removes them all. */
    List<String> race_ids,
    String language_race_id,
    /** The job; an empty string removes it. */
    String job_id,

    Integer current_hp,
    Integer current_wp,
    Integer current_ep,
    Integer current_mp,
    @Min(0) Integer temp_hp,
    Integer hp_bonus,
    Integer wp_bonus,
    Integer ep_bonus,
    Integer mp_bonus,
    Integer physical_damage_bonus,
    Integer magic_damage_bonus,
    Integer priority_bonus,
    Integer movement_bonus,

    @Valid List<AttributeEntry> attributes,
    @Valid List<SkillEntry> skills,
    @Valid List<TraitEntry> traits,
    @Valid List<PowerEntry> powers,
    @Valid InventoryEntry inventory
) {

    /** EXP, job points, and bonuses on one attribute. */
    public record AttributeEntry(
        @NotNull CharacterAttributeName attribute,
        @Min(0) int exp_spent,
        @Min(0) int job_points_spent,
        int bonus,
        int modifier
    ) {
    }

    /** One skill's creation swap, points, failures, and modifiers. */
    public record SkillEntry(
        @NotBlank String skill_id,
        @Min(-20) int creation_adjustment,
        @Min(0) int exp_spent,
        @Min(0) int job_points_spent,
        @Min(0) int failures,
        int permanent_modifier,
        int temporary_modifier
    ) {
    }

    /**
     * One trait. {@code edits} holds only the fields the player changed for this character (trait_name,
     * purchase_cost, lvl_up_cost, trait_desc, trait_uses, mp_cost, ep_cost, tp_cost, trait_effect, lvl_up_effect);
     * every other field follows the compendium.
     */
    public record TraitEntry(
        @NotBlank String trait_id,
        @NotNull CharacterTraitSource source,
        String race_id,
        @Min(1) @Max(6) int lvl,
        @Min(0) int exp_spent,
        String notes,
        Map<String, Object> edits
    ) {
    }

    /** One power, with the fields the player changed in {@code edits} (as for traits). */
    public record PowerEntry(
        @NotBlank String power_id,
        @Min(1) int lvl,
        @Min(0) int exp_spent,
        String notes,
        Map<String, Object> edits
    ) {
    }

    /** The whole inventory. Slots and items are replaced; items point at their slot by the slot's key. */
    public record InventoryEntry(
        @Min(0) int platinum,
        @Min(0) int gold,
        @Min(0) int silver,
        @Min(0) int bronze,
        @Min(0) int attunement_limit,
        @Min(1) int stack_limit_multiplier,
        String notes,
        @Valid @NotNull List<SlotEntry> slots,
        @Valid @NotNull List<SlotEntry> attunement_slots,
        @Valid @NotNull List<ItemEntry> items
    ) {
    }

    /** A slot. {@code key} is any id the editor uses for it, unique within the save. */
    public record SlotEntry(
        String key,
        @Size(max = 100) String label,
        @Size(max = 100) String group_name,
        String notes,
        int sort_order
    ) {
    }

    /** An item stack and where it is ({@code slot_key} for location SLOT). */
    public record ItemEntry(
        String item_id,
        @Size(max = 200) String custom_name,
        @NotNull InventoryLocation location,
        String slot_key,
        @Min(1) int quantity,
        @Min(1) Integer stack_limit,
        boolean attuned,
        @Min(0) Integer charges_remaining,
        String notes,
        int sort_order
    ) {
    }
}
