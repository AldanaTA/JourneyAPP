package com.journey.api_rest_v1.service;

import org.springframework.data.domain.Page;

import com.journey.api_rest_v1.dto.item.CreateItemRequest;
import com.journey.api_rest_v1.dto.item.ItemFilterRequest;
import com.journey.api_rest_v1.dto.item.ItemReferenceDataDto;
import com.journey.api_rest_v1.dto.item.ItemResponseDto;

/**
 * Business operations for compendium items: CRUD, paging, filtering, and weapon reference data.
 */
public interface ItemService {

    /** Returns a sorted page of all items, with inheritance resolved. */
    Page<ItemResponseDto> getAllItems(int page, int size, String sortBy, String sortDir);

    /** Returns a page of items matching every filter set on the request, ranked by name when searching. */
    Page<ItemResponseDto> filterItems(ItemFilterRequest request);

    /** Returns the weapon types, weapon properties, and damage types. */
    ItemReferenceDataDto getReferenceData();

    /** Creates a new item (optionally based on another item) and returns it. */
    ItemResponseDto createItem(CreateItemRequest request);

    /** Returns one item by its UUID string, with inheritance resolved. */
    ItemResponseDto getItemById(String id);

    /** Replaces an existing item's fields and returns the updated item. */
    ItemResponseDto updateItem(String id, CreateItemRequest request);

    /** Deletes an item that no other item is based on or made of, and returns a confirmation message. */
    String deleteItem(String id);
}
