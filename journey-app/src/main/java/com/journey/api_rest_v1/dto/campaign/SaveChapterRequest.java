package com.journey.api_rest_v1.dto.campaign;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A whole campaign chapter as the editor has it, saved in one request. {@code sections} (in reading order)
 * replaces the chapter's sections: listed ids are kept and updated, new ones have no id, missing ones are deleted.
 * The heading tree comes from the levels (each section sits under the nearest earlier section with a lower level).
 * Bodies are markdown like the rest of the rules: links such as [Raise Dead](power:<uuid>) are shown as cards.
 */
public record SaveChapterRequest(
    @Size(max = 200) String title,
    @Size(max = 5000) String description,
    @Valid @Size(max = 500) List<Section> sections
) {
    public record Section(
        String id,
        @NotBlank @Size(max = 200) String title,
        @Min(1) @Max(6) int level,
        @Size(max = 100_000) String body
    ) {
    }
}
