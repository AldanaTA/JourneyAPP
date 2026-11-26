package com.journey.api_rest_v1.dto.character;

/**
 * Request body for choosing (or clearing, with null) a character's job. Changing the job removes the
 * previous job's free traits, which returns their job points.
 */
public record SetCharacterJobRequest(
    String job_id
) {
}
