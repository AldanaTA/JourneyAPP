package com.journey.api_rest_v1.dto.rules;

import java.io.Serial;
import java.io.Serializable;

/** A chapter as listed in a book's table of contents. */
public record RuleChapterSummaryDto(String id, String title, Integer number, int sortOrder, String contentSourceName)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
