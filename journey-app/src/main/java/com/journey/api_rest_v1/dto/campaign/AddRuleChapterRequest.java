package com.journey.api_rest_v1.dto.campaign;

import jakarta.validation.constraints.Size;

/**
 * Adds a chapter at the end of a campaign book: a new chapter ({@code title}), or an official chapter by
 * reference ({@code chapter_id}, e.g. the core Combat chapter), which stays read-only and up to date.
 */
public record AddRuleChapterRequest(
    @Size(max = 200) String title,
    @Size(max = 5000) String description,
    String chapter_id
) {
}
