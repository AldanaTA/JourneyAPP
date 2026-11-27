package com.journey.api_rest_v1.dto.campaign;

import jakarta.validation.constraints.Size;

/**
 * Creates or changes a note; on change, only the set fields change. {@code folder_id}: a folder, or "" for the top
 * level.
 */
public record SaveNoteRequest(
    String folder_id,
    @Size(max = 200) String title,
    String body,
    Integer sort_order
) {
}
