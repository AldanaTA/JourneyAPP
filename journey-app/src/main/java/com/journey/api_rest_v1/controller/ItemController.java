package com.journey.api_rest_v1.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.item.CreateItemRequest;
import com.journey.api_rest_v1.dto.item.ItemFilterRequest;
import com.journey.api_rest_v1.dto.item.ItemReferenceDataDto;
import com.journey.api_rest_v1.dto.item.ItemResponseDto;
import com.journey.api_rest_v1.service.ItemService;

import com.journey.api_rest_v1.service.campaign.ContentAccess;

import jakarta.validation.Valid;

/**
 * REST endpoints for compendium items under {@code /api/v1/items}.
 * Mirrors {@link RaceController}: CRUD, a paged list, and a POST filter endpoint,
 * plus the weapon reference data (types, properties, damage types).
 */
@RestController
@RequestMapping("/api/v1/items")
public class ItemController {

    private final ItemService itemService;
    private final ContentAccess contentAccess;

    /** Wires the item service. */
    public ItemController(ItemService itemService, ContentAccess contentAccess) {
        this.contentAccess = contentAccess;
        this.itemService = itemService;
    }

    /** Creates an item (optionally based on another item) and returns it with 201 Created. */
    @PostMapping()
    public ResponseEntity<ItemResponseDto> createItem(@Valid @RequestBody CreateItemRequest request) {
        contentAccess.requireWritableSource(request.content_source_id());
        return new ResponseEntity<>(itemService.createItem(request), HttpStatus.CREATED);
    }

    /** Returns the weapon types, weapon properties, and damage types. */
    @GetMapping("/reference-data")
    public ResponseEntity<ItemReferenceDataDto> getReferenceData() {
        return new ResponseEntity<>(itemService.getReferenceData(), HttpStatus.OK);
    }

    /** Returns a single item by id, with inheritance resolved. */
    @GetMapping("/{id}")
    public ResponseEntity<ItemResponseDto> getItem(@PathVariable String id) {
        contentAccess.requireReadable(ContentAccess.Kind.ITEM, id);
        return new ResponseEntity<>(itemService.getItemById(id), HttpStatus.OK);
    }

    /** Returns a sorted page of all items. */
    @GetMapping()
    public ResponseEntity<Iterable<ItemResponseDto>> getAllItems(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "5") int size,
        @RequestParam(defaultValue = "name") String sortBy,
        @RequestParam(defaultValue = "asc") String sortDir) {
        return new ResponseEntity<>(itemService.getAllItems(page, size, sortBy, sortDir), HttpStatus.OK);
    }

    /** Returns a page of items matching the filters in the request body. */
    @PostMapping("/filter-by-category")
    public ResponseEntity<Iterable<ItemResponseDto>> filterItems(@Valid @RequestBody ItemFilterRequest request) {
        return new ResponseEntity<>(itemService.filterItems(request), HttpStatus.OK);
    }

    /** Replaces an existing item and returns the updated version. */
    @PutMapping("/{id}")
    public ResponseEntity<ItemResponseDto> updateItem(@PathVariable String id,
            @Valid @RequestBody CreateItemRequest request) {
        contentAccess.requireWritable(ContentAccess.Kind.ITEM, id);
        contentAccess.requireWritableSource(request.content_source_id());
        return new ResponseEntity<>(itemService.updateItem(id, request), HttpStatus.OK);
    }

    /** Deletes an item that no other item is based on or made of. */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteItem(@PathVariable String id) {
        contentAccess.requireWritable(ContentAccess.Kind.ITEM, id);
        return ResponseEntity.ok(itemService.deleteItem(id));
    }
}
