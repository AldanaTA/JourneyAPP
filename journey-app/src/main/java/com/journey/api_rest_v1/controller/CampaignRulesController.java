package com.journey.api_rest_v1.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.campaign.AddRuleChapterRequest;
import com.journey.api_rest_v1.dto.campaign.ReorderChaptersRequest;
import com.journey.api_rest_v1.dto.campaign.SaveChapterRequest;
import com.journey.api_rest_v1.dto.campaign.SaveRuleBookRequest;
import com.journey.api_rest_v1.dto.rules.RuleBookDto;
import com.journey.api_rest_v1.dto.rules.RuleChapterDto;
import com.journey.api_rest_v1.service.campaign.CampaignRulesService;
import com.journey.api_rest_v1.service.impl.CharacterAccess;

import jakarta.validation.Valid;

/**
 * A campaign's own rules (storytellers write; members list). Reading a book or chapter, and searching, use the
 * usual /api/v1/rules endpoints, which only show campaign rules to the campaign's members.
 */
@RestController
@RequestMapping("/api/v1/campaigns/{id}/rules")
public class CampaignRulesController {

    private final CampaignRulesService rules;

    public CampaignRulesController(CampaignRulesService rules) {
        this.rules = rules;
    }

    /** The campaign's books with their chapters. */
    @GetMapping("/books")
    public ResponseEntity<List<RuleBookDto>> books(@PathVariable String id) {
        return ResponseEntity.ok(rules.books(uuid(id, "campaign")));
    }

    @PostMapping("/books")
    public ResponseEntity<RuleBookDto> createBook(@PathVariable String id, @Valid @RequestBody SaveRuleBookRequest request) {
        return new ResponseEntity<>(rules.createBook(uuid(id, "campaign"), request), HttpStatus.CREATED);
    }

    @PatchMapping("/books/{bookId}")
    public ResponseEntity<RuleBookDto> updateBook(@PathVariable String id, @PathVariable String bookId,
            @Valid @RequestBody SaveRuleBookRequest request) {
        return ResponseEntity.ok(rules.updateBook(uuid(id, "campaign"), uuid(bookId, "book"), request));
    }

    /** Deletes the book and the campaign chapters only it used. */
    @DeleteMapping("/books/{bookId}")
    public ResponseEntity<Void> deleteBook(@PathVariable String id, @PathVariable String bookId) {
        rules.deleteBook(uuid(id, "campaign"), uuid(bookId, "book"));
        return ResponseEntity.noContent().build();
    }

    /** Adds a new chapter, or an official one by reference ({@code chapter_id}), at the end of the book. */
    @PostMapping("/books/{bookId}/chapters")
    public ResponseEntity<RuleBookDto> addChapter(@PathVariable String id, @PathVariable String bookId,
            @Valid @RequestBody AddRuleChapterRequest request) {
        return new ResponseEntity<>(rules.addChapter(uuid(id, "campaign"), uuid(bookId, "book"), request), HttpStatus.CREATED);
    }

    /** Takes a chapter out of the book (deleting it if it is the campaign's own and no other book uses it). */
    @DeleteMapping("/books/{bookId}/chapters/{chapterId}")
    public ResponseEntity<RuleBookDto> removeChapter(@PathVariable String id, @PathVariable String bookId,
            @PathVariable String chapterId) {
        return ResponseEntity.ok(rules.removeChapter(uuid(id, "campaign"), uuid(bookId, "book"), uuid(chapterId, "chapter")));
    }

    @PutMapping("/books/{bookId}/chapter-order")
    public ResponseEntity<RuleBookDto> reorder(@PathVariable String id, @PathVariable String bookId,
            @Valid @RequestBody ReorderChaptersRequest request) {
        return ResponseEntity.ok(rules.reorderChapters(uuid(id, "campaign"), uuid(bookId, "book"), request.chapter_ids()));
    }

    /** Saves a whole chapter of the campaign: title, description, and every section in reading order. */
    @PutMapping("/chapters/{chapterId}")
    public ResponseEntity<RuleChapterDto> saveChapter(@PathVariable String id, @PathVariable String chapterId,
            @Valid @RequestBody SaveChapterRequest request) {
        return ResponseEntity.ok(rules.saveChapter(uuid(id, "campaign"), uuid(chapterId, "chapter"), request));
    }

    private static UUID uuid(String value, String what) {
        return CharacterAccess.parseUuid(value, what);
    }
}
