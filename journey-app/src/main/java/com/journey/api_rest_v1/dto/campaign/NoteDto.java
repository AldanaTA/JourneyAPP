package com.journey.api_rest_v1.dto.campaign;

/** One note: markdown in the rules' format (entity links like [Raise Dead](power:<uuid>) work). */
public record NoteDto(
    String id,
    String folderId,
    String title,
    String body,
    int sortOrder,
    String authorName,
    String editedByName,
    String createdAt,
    String updatedAt
) {
}
