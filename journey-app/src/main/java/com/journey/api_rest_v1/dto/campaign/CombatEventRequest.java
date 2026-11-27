package com.journey.api_rest_v1.dto.campaign;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A combat log entry: {@code event} names it (damage, healing, condition, turn, ...), {@code summary} is the line
 * people read ("Captain Vey hits Quill for 7 fire"), and {@code details} holds whatever the event needs
 * (target, amount, damage type, the turn order).
 */
public record CombatEventRequest(
    @NotBlank @Size(max = 50) String event,
    @NotBlank @Size(max = 1000) String summary,
    @NotNull Map<String, Object> details,
    Boolean whisper,
    @Size(max = 50) List<String> whisper_to
) {
}
