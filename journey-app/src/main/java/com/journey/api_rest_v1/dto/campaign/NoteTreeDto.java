package com.journey.api_rest_v1.dto.campaign;

import java.util.List;

/** The campaign's folders and note titles (no note bodies: those are fetched one note at a time). */
public record NoteTreeDto(
    List<FolderDto> folders,
    List<NoteSummaryDto> notes
) {
    public record FolderDto(String id, String parentId, String name, int sortOrder) {
    }

    public record NoteSummaryDto(String id, String folderId, String title, int sortOrder, String updatedAt) {
    }
}
