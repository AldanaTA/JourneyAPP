package com.journey.api_rest_v1.service;

import java.util.List;

import com.journey.api_rest_v1.dto.rules.RuleBookDto;
import com.journey.api_rest_v1.dto.rules.RuleChapterDto;
import com.journey.api_rest_v1.dto.rules.RuleSearchResultDto;

/**
 * Read operations for rules: books with their tables of contents, whole chapters ready to render, and
 * full-text search. Rules are written by the rules importer (agent services) for now.
 */
public interface RuleService {

    /** Returns every book with its chapters in reading order. */
    List<RuleBookDto> getBooks();

    /** Returns one book with its chapters in reading order. */
    RuleBookDto getBookById(String id);

    /** Returns a chapter's sections in reading order with the entities and images they reference. */
    RuleChapterDto getChapterById(String id);

    /** Searches section titles and bodies, optionally within one book; best matches first. */
    List<RuleSearchResultDto> search(String query, String bookId, int limit);
}
