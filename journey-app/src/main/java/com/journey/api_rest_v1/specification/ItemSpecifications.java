package com.journey.api_rest_v1.specification;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.journey.api_rest_v1.domain.items.Item;
import com.journey.api_rest_v1.domain.items.ItemRarity;
import com.journey.api_rest_v1.domain.items.ItemType;
import com.journey.api_rest_v1.domain.items.ItemWeapon;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Reusable JPA Specifications used to build dynamic item filter queries.
 */
public final class ItemSpecifications {

    /** Utility class; not meant to be instantiated. */
    private ItemSpecifications() {
    }

    /** Returns a specification that matches every item; used as the starting point for chaining. */
    public static Specification<Item> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    /** Matches items of any of the given types. */
    public static Specification<Item> withTypes(List<ItemType> types) {
        if (types == null || types.isEmpty()) {
            return all();
        }
        return (root, query, cb) -> root.get("itemType").in(types);
    }

    /** Matches items of any of the given rarities. */
    public static Specification<Item> withRarities(List<ItemRarity> rarities) {
        if (rarities == null || rarities.isEmpty()) {
            return all();
        }
        return (root, query, cb) -> root.get("rarity").in(rarities);
    }

    /** Matches magic items only, when requested. */
    public static Specification<Item> magicOnly(boolean magicOnly) {
        return magicOnly ? (root, query, cb) -> cb.isTrue(root.get("magic")) : all();
    }

    /** Matches items that require attunement only, when requested. */
    public static Specification<Item> attunementOnly(boolean attunementOnly) {
        return attunementOnly ? (root, query, cb) -> cb.isTrue(root.get("requiresAttunement")) : all();
    }

    /** Matches weapons whose (own or directly inherited) weapon type is one of the given names. */
    public static Specification<Item> withWeaponTypes(List<String> weaponTypes) {
        return withWeaponStat("weaponType", weaponTypes);
    }

    /** Matches weapons whose (own or directly inherited) damage type is one of the given names. */
    public static Specification<Item> withDamageTypes(List<String> damageTypes) {
        return withWeaponStat("damageType", damageTypes);
    }

    /** Matches the item whose database-generated {@code name_key} equals {@code nameKey}. */
    public static Specification<Item> withExactName(String nameKey) {
        return (root, query, cb) -> cb.equal(root.<String>get("nameKey"), nameKey);
    }

    /**
     * Matches items whose weapon stat (weaponType or damageType) has one of the given names.
     * An item with its own weapon row is matched on that row; an item without one is matched on its
     * base item's row (one level of inheritance, which covers items derived from book weapons).
     */
    private static Specification<Item> withWeaponStat(String statField, List<String> names) {
        if (names == null || names.isEmpty()) {
            return all();
        }

        List<String> keys = names.stream().map(name -> name.trim().toLowerCase(Locale.ROOT)).distinct().toList();

        return (root, query, cb) -> {
            Subquery<UUID> matching = weaponItemIds(query, cb, statField, keys);
            Subquery<UUID> withOwnWeapon = query.subquery(UUID.class);
            Root<ItemWeapon> ownRoot = withOwnWeapon.from(ItemWeapon.class);
            withOwnWeapon.select(ownRoot.get("itemId"));

            Join<Item, Item> base = root.join("baseItem", JoinType.LEFT);

            Predicate ownMatches = root.get("id").in(matching);
            Predicate inheritsMatch = cb.and(
                    cb.not(root.get("id").in(withOwnWeapon)),
                    base.get("id").in(weaponItemIds(query, cb, statField, keys)));
            return cb.or(ownMatches, inheritsMatch);
        };
    }

    /** Ids of items whose own weapon row has the given stat in {@code keys}. */
    private static Subquery<UUID> weaponItemIds(CriteriaQuery<?> query, CriteriaBuilder cb, String statField,
            List<String> keys) {
        Subquery<UUID> subquery = query.subquery(UUID.class);
        Root<ItemWeapon> weapon = subquery.from(ItemWeapon.class);
        subquery.select(weapon.get("itemId")).where(weapon.get(statField).get("nameKey").in(keys));
        return subquery;
    }
}
