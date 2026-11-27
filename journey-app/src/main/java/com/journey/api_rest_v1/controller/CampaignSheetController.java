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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.campaign.SaveSheetRequest;
import com.journey.api_rest_v1.dto.character.CharacterSheetDto;
import com.journey.api_rest_v1.service.campaign.CampaignSheetService;
import com.journey.api_rest_v1.service.impl.CharacterAccess;

import jakarta.validation.Valid;

/** A campaign's own character sheets (starting attributes). Each call answers with the campaign's sheets. */
@RestController
@RequestMapping("/api/v1/campaigns/{id}/sheets")
public class CampaignSheetController {

    private final CampaignSheetService sheets;

    public CampaignSheetController(CampaignSheetService sheets) {
        this.sheets = sheets;
    }

    @GetMapping
    public ResponseEntity<List<CharacterSheetDto>> list(@PathVariable String id) {
        return ResponseEntity.ok(sheets.list(uuid(id, "campaign")));
    }

    @PostMapping
    public ResponseEntity<List<CharacterSheetDto>> create(@PathVariable String id, @Valid @RequestBody SaveSheetRequest request) {
        return new ResponseEntity<>(sheets.create(uuid(id, "campaign"), request), HttpStatus.CREATED);
    }

    @PatchMapping("/{sheetId}")
    public ResponseEntity<List<CharacterSheetDto>> update(@PathVariable String id, @PathVariable String sheetId,
            @Valid @RequestBody SaveSheetRequest request) {
        return ResponseEntity.ok(sheets.update(uuid(id, "campaign"), uuid(sheetId, "sheet"), request));
    }

    @DeleteMapping("/{sheetId}")
    public ResponseEntity<List<CharacterSheetDto>> delete(@PathVariable String id, @PathVariable String sheetId) {
        return ResponseEntity.ok(sheets.delete(uuid(id, "campaign"), uuid(sheetId, "sheet")));
    }

    private static UUID uuid(String value, String what) {
        return CharacterAccess.parseUuid(value, what);
    }
}
