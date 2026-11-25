package com.journey.api_rest_v1.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.rules.RuleBookDto;
import com.journey.api_rest_v1.dto.rules.RuleChapterDto;
import com.journey.api_rest_v1.dto.rules.RuleSearchResultDto;
import com.journey.api_rest_v1.service.RuleService;

/**
 * Read-only REST endpoints for rules under {@code /api/v1/rules}: books (tables of contents), whole chapters
 * ready to render, and full-text search. Rules are written by the rules importer for now.
 */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleService ruleService;

    /** Wires the rule service. */
    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    /** Returns every book with its chapters in reading order. */
    @GetMapping("/books")
    public ResponseEntity<List<RuleBookDto>> getBooks() {
        return new ResponseEntity<>(ruleService.getBooks(), HttpStatus.OK);
    }

    /** Returns one book with its chapters in reading order. */
    @GetMapping("/books/{id}")
    public ResponseEntity<RuleBookDto> getBookById(@PathVariable String id) {
        return new ResponseEntity<>(ruleService.getBookById(id), HttpStatus.OK);
    }

    /** Returns a chapter's sections plus the entities and images they reference. */
    @GetMapping("/chapters/{id}")
    public ResponseEntity<RuleChapterDto> getChapterById(@PathVariable String id) {
        return new ResponseEntity<>(ruleService.getChapterById(id), HttpStatus.OK);
    }

    /** Searches rules text (web-search syntax), optionally within one book; best matches first. */
    @GetMapping("/search")
    public ResponseEntity<List<RuleSearchResultDto>> search(
        @RequestParam("q") String query,
        @RequestParam(required = false) String bookId,
        @RequestParam(defaultValue = "20") int limit) {
        return new ResponseEntity<>(ruleService.search(query, bookId, limit), HttpStatus.OK);
    }
}
