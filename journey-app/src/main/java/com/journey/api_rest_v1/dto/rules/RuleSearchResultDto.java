package com.journey.api_rest_v1.dto.rules;

import java.io.Serial;
import java.io.Serializable;

/** A section matching a rules search. In snippet, matched words are wrapped in « and ». */
public record RuleSearchResultDto(String sectionId, String sectionTitle, String slug, String chapterId, String chapterTitle,
        String bookId, String snippet, double rank)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
