package com.journey.api_rest_v1.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.journey.api_rest_v1.domain.items.Item;

/**
 * Spring Data repository for items, with Specification support for dynamic filtering.
 */
public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {

    /** Returns true when an item already uses this normalized (lowercase, trimmed) name. */
    boolean existsByNameKey(String nameKey);

    /** Returns true when an item other than {@code id} already uses this normalized name. */
    boolean existsByNameKeyAndIdNot(String nameKey, UUID id);

    /** Returns true when any item is based on the given item. */
    boolean existsByBaseItemId(UUID baseItemId);

    /** Returns true when any item uses the given item as its material. */
    boolean existsByMaterialId(UUID materialId);
}
