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

import com.journey.api_rest_v1.dto.campaign.CampaignCharacterDto;
import com.journey.api_rest_v1.dto.campaign.CampaignDto;
import com.journey.api_rest_v1.dto.campaign.CampaignMemberDto;
import com.journey.api_rest_v1.dto.campaign.CampaignSummaryDto;
import com.journey.api_rest_v1.dto.campaign.CheckImportRequest;
import com.journey.api_rest_v1.dto.campaign.CreateCampaignRequest;
import com.journey.api_rest_v1.dto.campaign.ImportCharacterRequest;
import com.journey.api_rest_v1.dto.campaign.ImportCheckDto;
import com.journey.api_rest_v1.dto.campaign.JoinPreviewDto;
import com.journey.api_rest_v1.dto.campaign.SetAllowedSourcesRequest;
import com.journey.api_rest_v1.dto.campaign.TransferOwnershipRequest;
import com.journey.api_rest_v1.dto.campaign.UpdateCampaignRequest;
import com.journey.api_rest_v1.dto.campaign.UpdateMemberRequest;
import com.journey.api_rest_v1.service.campaign.CampaignService;
import com.journey.api_rest_v1.service.impl.CharacterAccess;

import jakarta.validation.Valid;

/**
 * Campaigns, their join link, members, allowed sources, and campaign versions of characters. Notes are in
 * {@link CampaignNoteController}; a campaign version is opened and saved with the usual /characters endpoints.
 */
@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

    private final CampaignService campaigns;

    public CampaignController(CampaignService campaigns) {
        this.campaigns = campaigns;
    }

    /** The campaigns the user owns, runs, or plays in. */
    @GetMapping
    public ResponseEntity<List<CampaignSummaryDto>> listMine() {
        return ResponseEntity.ok(campaigns.listMine());
    }

    /** Creates a campaign; the user becomes its owner. */
    @PostMapping
    public ResponseEntity<CampaignDto> create(@Valid @RequestBody CreateCampaignRequest request) {
        return new ResponseEntity<>(campaigns.create(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignDto> get(@PathVariable String id) {
        return ResponseEntity.ok(campaigns.get(uuid(id)));
    }

    /** Name, description, or whether the join link works (storytellers). */
    @PatchMapping("/{id}")
    public ResponseEntity<CampaignDto> update(@PathVariable String id, @Valid @RequestBody UpdateCampaignRequest request) {
        return ResponseEntity.ok(campaigns.update(uuid(id), request));
    }

    /** Deletes the campaign with its content, rules, notes, and character versions (owner). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        campaigns.delete(uuid(id));
        return ResponseEntity.noContent().build();
    }

    /** A new join code; old links stop working. {@code ?role=STORYTELLER} resets the co-storyteller code (owner). */
    @PostMapping("/{id}/join-code")
    public ResponseEntity<CampaignDto> resetJoinCode(@PathVariable String id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "PLAYER") String role) {
        return ResponseEntity.ok(campaigns.resetJoinCode(uuid(id), "STORYTELLER".equalsIgnoreCase(role)));
    }

    /** What a join link leads to, before joining. */
    @GetMapping("/join/{code}")
    public ResponseEntity<JoinPreviewDto> previewJoin(@PathVariable String code) {
        return ResponseEntity.ok(campaigns.previewJoin(code));
    }

    /** Joins the campaign as a player. */
    @PostMapping("/join/{code}")
    public ResponseEntity<CampaignDto> join(@PathVariable String code) {
        return ResponseEntity.ok(campaigns.join(code));
    }

    // ----------------------------
    // Members
    // ----------------------------

    @GetMapping("/{id}/members")
    public ResponseEntity<List<CampaignMemberDto>> members(@PathVariable String id) {
        return ResponseEntity.ok(campaigns.members(uuid(id)));
    }

    /** Makes a member a co-storyteller or a player (owner). */
    @PatchMapping("/{id}/members/{userId}")
    public ResponseEntity<List<CampaignMemberDto>> setRole(@PathVariable String id, @PathVariable String userId,
            @Valid @RequestBody UpdateMemberRequest request) {
        return ResponseEntity.ok(campaigns.setRole(uuid(id), CharacterAccess.parseUuid(userId, "user"), request.role()));
    }

    /** Removes a member, or leaves the campaign when it is the caller's own id. */
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable String id, @PathVariable String userId) {
        campaigns.removeMember(uuid(id), CharacterAccess.parseUuid(userId, "user"));
        return ResponseEntity.noContent().build();
    }

    /** Hands the campaign to another member (owner). */
    @PostMapping("/{id}/owner")
    public ResponseEntity<CampaignDto> transferOwnership(@PathVariable String id, @Valid @RequestBody TransferOwnershipRequest request) {
        return ResponseEntity.ok(campaigns.transferOwnership(uuid(id), CharacterAccess.parseUuid(request.user_id(), "user")));
    }

    // ----------------------------
    // Allowed sources
    // ----------------------------

    /** The books characters may use besides the core rules and the campaign's own content (storytellers). */
    @PutMapping("/{id}/sources")
    public ResponseEntity<CampaignDto> setAllowedSources(@PathVariable String id, @Valid @RequestBody SetAllowedSourcesRequest request) {
        return ResponseEntity.ok(campaigns.setAllowedSources(uuid(id), request.source_ids()));
    }

    // ----------------------------
    // Characters
    // ----------------------------

    @GetMapping("/{id}/characters")
    public ResponseEntity<List<CampaignCharacterDto>> characters(@PathVariable String id) {
        return ResponseEntity.ok(campaigns.characters(uuid(id)));
    }

    /**
     * Before bringing a character in: what the campaign version would lose (content from sources the campaign does
     * not allow). Nothing changes; the player can then import, pick another character, or make a new one.
     */
    @PostMapping("/{id}/characters/import-check")
    public ResponseEntity<ImportCheckDto> checkImport(@PathVariable String id, @Valid @RequestBody CheckImportRequest request) {
        return ResponseEntity.ok(campaigns.checkImport(uuid(id), request.character_id()));
    }

    /**
     * Brings one of the player's characters in as a campaign version (a deep copy; the original is not changed).
     * 201 with the new campaign version, or 409 with the import check when something would be removed and
     * {@code accept_removals} is not true.
     */
    @PostMapping("/{id}/characters")
    public ResponseEntity<?> importCharacter(@PathVariable String id, @Valid @RequestBody ImportCharacterRequest request) {
        CampaignService.ImportResult result = campaigns.importCharacter(uuid(id), request.character_id(),
                Boolean.TRUE.equals(request.accept_removals()));
        if (result.confirmationNeeded() != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result.confirmationNeeded());
        }
        return new ResponseEntity<>(result.character(), HttpStatus.CREATED);
    }

    /** Takes a campaign version out of the campaign, deleting it (its player or a storyteller). */
    @DeleteMapping("/{id}/characters/{characterId}")
    public ResponseEntity<Void> removeCharacter(@PathVariable String id, @PathVariable String characterId) {
        campaigns.removeCharacter(uuid(id), CharacterAccess.parseUuid(characterId, "character"));
        return ResponseEntity.noContent().build();
    }

    private static UUID uuid(String id) {
        return CharacterAccess.parseUuid(id, "campaign");
    }
}
