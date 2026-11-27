package com.journey.api_rest_v1.dto.campaign;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A chat message. To whisper, set {@code whisper} and list the members it is for in {@code whisper_to}; with no
 * one listed it goes to the storytellers only. Storytellers always see whispers.
 */
public record SendMessageRequest(
    @NotBlank @Size(max = 4000) String body,
    Boolean whisper,
    @Size(max = 50) List<String> whisper_to
) {
}
