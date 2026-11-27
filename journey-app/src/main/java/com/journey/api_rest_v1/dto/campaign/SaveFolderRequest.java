package com.journey.api_rest_v1.dto.campaign;

import jakarta.validation.constraints.Size;

/**
 * Creates or changes a folder; on change, only the set fields change. {@code parent_id}: a folder, or "" for the
 * top level.
 */
public record SaveFolderRequest(
    String parent_id,
    @Size(max = 100) String name,
    Integer sort_order
) {
}
