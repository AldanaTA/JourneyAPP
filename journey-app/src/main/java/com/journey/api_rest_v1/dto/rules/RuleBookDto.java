package com.journey.api_rest_v1.dto.rules;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** A book with its chapters in reading order. */
public record RuleBookDto(String id, String title, String description, String contentSourceName,
        List<RuleChapterSummaryDto> chapters)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
