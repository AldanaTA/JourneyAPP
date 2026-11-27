package com.journey.api_rest_v1.dto.campaign;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * A dice roll, rolled by the server. Either a skill roll (a d100 against {@code skill_rating}, with the Degrees of
 * Success) or a {@code formula} such as 2d6+3. {@code whisper} with nobody in {@code whisper_to} is a roll only the
 * storytellers see (a secret roll when a storyteller makes it).
 *
 * @param label     what is rolled for ("Dodge", "Fire Bolt damage")
 * @param modifier  a situational change to the skill rating, e.g. -20
 */
public record RollRequest(
    @Size(max = 100) String formula,
    @Size(max = 100) String label,
    @Min(0) @Max(1000) Integer skill_rating,
    @Min(-1000) @Max(1000) Integer modifier,
    Boolean whisper,
    @Size(max = 50) List<String> whisper_to
) {
}
