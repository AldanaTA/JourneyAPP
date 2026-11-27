package com.journey.api_rest_v1.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.campaign.ImportCheckDto;
import com.journey.api_rest_v1.service.campaign.CharacterExportService;

/** Export a character (e.g. a campaign version) to the player's own characters. */
@RestController
@RequestMapping("/api/v1/characters/{id}")
public class CharacterExportController {

    private final CharacterExportService exports;

    public CharacterExportController(CharacterExportService exports) {
        this.exports = exports;
    }

    /** What the copy would leave out (content from sources the player does not own). Changes nothing. */
    @PostMapping("/export-check")
    public ResponseEntity<ImportCheckDto> check(@PathVariable String id) {
        return ResponseEntity.ok(exports.check(id));
    }

    /** Makes the copy in the player's own characters; returns {"id", "name"}. */
    @PostMapping("/export")
    public ResponseEntity<Map<String, String>> export(@PathVariable String id) {
        return new ResponseEntity<>(exports.export(id), HttpStatus.CREATED);
    }
}
