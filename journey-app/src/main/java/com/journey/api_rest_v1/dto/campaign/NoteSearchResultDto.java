package com.journey.api_rest_v1.dto.campaign;

/** A note matching a search; {@code snippet} marks the matching words like the rules search does. */
public record NoteSearchResultDto(
    String id,
    String folderId,
    String title,
    String snippet
) {
}
