package com.journey.api_rest_v1.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.source.ContentSourceDto;
import com.journey.api_rest_v1.repository.ContentSourceRepository;
import com.journey.api_rest_v1.service.CurrentUser;

/** The content sources (core rules, books, campaigns) the signed-in user can read. */
@RestController
@RequestMapping("/api/v1/content-sources")
public class ContentSourceController {

    private final ContentSourceRepository repository;
    private final CurrentUser currentUser;

    public ContentSourceController(ContentSourceRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ContentSourceDto>> listAvailable() {
        return ResponseEntity.ok(repository.findAvailable(currentUser.requireId(), currentUser.isAdmin()).stream()
                .map(s -> new ContentSourceDto(s.getId().toString(), s.getName(), s.getDescription(), s.getKind()))
                .toList());
    }
}
