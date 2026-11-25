package com.journey.api_rest_v1.dto.rules;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.journey.api_rest_v1.dto.creature.CreatureResponseDto;
import com.journey.api_rest_v1.dto.item.ItemResponseDto;
import com.journey.api_rest_v1.dto.job.JobResponseDto;
import com.journey.api_rest_v1.dto.power.PowerResponseDto;
import com.journey.api_rest_v1.dto.race.RaceResponseDto;
import com.journey.api_rest_v1.dto.trait.TraitResponseDto;

/**
 * A whole chapter ready to render: its sections in reading order plus everything their bodies link to.
 * A body link such as {@code [Battle Tactics](trait:<id>)} is looked up in {@code traits} by id, and an image
 * {@code ![alt](asset:<id>)} in {@code assets}. Entities are the same DTOs the compendium endpoints return,
 * so the frontend can show them with the compendium cards. A link whose id is missing from these maps points
 * at an entity that was deleted after the chapter was imported.
 */
public record RuleChapterDto(
        String id,
        String title,
        Integer number,
        String description,
        String contentSourceName,
        List<RuleSectionDto> sections,
        Map<String, TraitResponseDto> traits,
        Map<String, PowerResponseDto> powers,
        Map<String, ItemResponseDto> items,
        Map<String, RaceResponseDto> races,
        Map<String, JobResponseDto> jobs,
        Map<String, CreatureResponseDto> creatures,
        Map<String, RuleAssetDto> assets)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
