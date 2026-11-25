package com.journey.api_rest_v1.dto.rules;

import java.io.Serial;
import java.io.Serializable;

/** One heading of a chapter and the Markdown under it; parentId builds the tree. */
public record RuleSectionDto(String id, String parentId, String title, int level, String slug, String body, int sortOrder)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
