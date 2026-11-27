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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.campaign.NoteDto;
import com.journey.api_rest_v1.dto.campaign.NoteSearchResultDto;
import com.journey.api_rest_v1.dto.campaign.NoteTreeDto;
import com.journey.api_rest_v1.dto.campaign.SaveFolderRequest;
import com.journey.api_rest_v1.dto.campaign.SaveNoteRequest;
import com.journey.api_rest_v1.service.campaign.CampaignNoteService;
import com.journey.api_rest_v1.service.impl.CharacterAccess;

import jakarta.validation.Valid;

/** The storytellers' notes for a campaign: folders and markdown notes (storytellers only). */
@RestController
@RequestMapping("/api/v1/campaigns/{id}")
public class CampaignNoteController {

    private final CampaignNoteService notes;

    public CampaignNoteController(CampaignNoteService notes) {
        this.notes = notes;
    }

    /** All folders and note titles. */
    @GetMapping("/notes")
    public ResponseEntity<NoteTreeDto> tree(@PathVariable String id) {
        return ResponseEntity.ok(notes.tree(uuid(id, "campaign")));
    }

    @GetMapping("/notes/search")
    public ResponseEntity<List<NoteSearchResultDto>> search(@PathVariable String id, @RequestParam("q") String query) {
        return ResponseEntity.ok(notes.search(uuid(id, "campaign"), query));
    }

    @GetMapping("/notes/{noteId}")
    public ResponseEntity<NoteDto> get(@PathVariable String id, @PathVariable String noteId) {
        return ResponseEntity.ok(notes.get(uuid(id, "campaign"), uuid(noteId, "note")));
    }

    @PostMapping("/notes")
    public ResponseEntity<NoteDto> createNote(@PathVariable String id, @Valid @RequestBody SaveNoteRequest request) {
        return new ResponseEntity<>(notes.createNote(uuid(id, "campaign"), request), HttpStatus.CREATED);
    }

    @PatchMapping("/notes/{noteId}")
    public ResponseEntity<NoteDto> updateNote(@PathVariable String id, @PathVariable String noteId,
            @Valid @RequestBody SaveNoteRequest request) {
        return ResponseEntity.ok(notes.updateNote(uuid(id, "campaign"), uuid(noteId, "note"), request));
    }

    @DeleteMapping("/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable String id, @PathVariable String noteId) {
        notes.deleteNote(uuid(id, "campaign"), uuid(noteId, "note"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/note-folders")
    public ResponseEntity<NoteTreeDto> createFolder(@PathVariable String id, @Valid @RequestBody SaveFolderRequest request) {
        return new ResponseEntity<>(notes.createFolder(uuid(id, "campaign"), request), HttpStatus.CREATED);
    }

    @PatchMapping("/note-folders/{folderId}")
    public ResponseEntity<NoteTreeDto> updateFolder(@PathVariable String id, @PathVariable String folderId,
            @Valid @RequestBody SaveFolderRequest request) {
        return ResponseEntity.ok(notes.updateFolder(uuid(id, "campaign"), uuid(folderId, "folder"), request));
    }

    /** Deletes a folder and everything in it. */
    @DeleteMapping("/note-folders/{folderId}")
    public ResponseEntity<NoteTreeDto> deleteFolder(@PathVariable String id, @PathVariable String folderId) {
        return ResponseEntity.ok(notes.deleteFolder(uuid(id, "campaign"), uuid(folderId, "folder")));
    }

    private static UUID uuid(String value, String what) {
        return CharacterAccess.parseUuid(value, what);
    }
}
