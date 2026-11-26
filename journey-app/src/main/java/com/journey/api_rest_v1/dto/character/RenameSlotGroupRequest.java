package com.journey.api_rest_v1.dto.character;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for renaming a group of slots: the group and its numbered labels change together
 * ("Spatial Storage 2" -> "Pocket Dimension 2"); slots whose label was changed by hand keep it.
 */
public record RenameSlotGroupRequest(
    @NotBlank @Size(max = 100) String name
) {
}
