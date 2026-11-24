package com.journey.api_rest_v1.specification;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.journey.api_rest_v1.domain.creatures.AugmentKind;
import com.journey.api_rest_v1.domain.creatures.Creature;
import com.journey.api_rest_v1.domain.creatures.CreatureAugment;
import com.journey.api_rest_v1.domain.races.RaceSize;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Reusable JPA Specifications used to build dynamic creature filter queries.
 */
public final class CreatureSpecifications {

    /** Utility class; not meant to be instantiated. */
    private CreatureSpecifications() {
    }

    /** Returns a specification that matches every creature; used as the starting point for chaining. */
    public static Specification<Creature> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    /** Matches creatures whose type name is any of the given names (across sources). */
    public static Specification<Creature> withTypes(List<String> typeNames) {
        if (typeNames == null || typeNames.isEmpty()) {
            return all();
        }
        List<String> keys = normalize(typeNames);
        return (root, query, cb) -> root.get("creatureType").get("nameKey").in(keys);
    }

    /** Matches creatures whose threat level is within the given bounds (either bound may be null). */
    public static Specification<Creature> withThreatLvl(Integer min, Integer max) {
        return (root, query, cb) -> {
            Path<Integer> threat = root.get("threatLvl");
            Predicate predicate = cb.conjunction();
            if (min != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(threat, min));
            }
            if (max != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(threat, max));
            }
            return predicate;
        };
    }

    /**
     * Matches creatures that can be any of the given sizes: a single-size creature must equal one of them,
     * and a ranged creature matches when a given size lies between its min and max. Sizes are compared with
     * explicit IN lists (like races) because the enum binds as its label, which sorts alphabetically.
     */
    public static Specification<Creature> withSizes(List<RaceSize> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return all();
        }

        return (root, query, cb) -> {
            Path<RaceSize> minSize = root.get("minSize");
            Path<RaceSize> maxSize = root.get("maxSize");

            Predicate[] matches = sizes.stream().distinct().map(size -> {
                List<RaceSize> atMost = Arrays.stream(RaceSize.values())
                        .filter(other -> other.ordinal() <= size.ordinal()).toList();
                List<RaceSize> atLeast = Arrays.stream(RaceSize.values())
                        .filter(other -> other.ordinal() >= size.ordinal()).toList();
                return cb.or(
                        cb.and(cb.isNull(maxSize), cb.equal(minSize, size)),
                        cb.and(minSize.in(atMost), maxSize.in(atLeast)));
            }).toArray(Predicate[]::new);

            return cb.or(matches);
        };
    }

    /**
     * Matches creatures with an augment of the given kind against any of the named damage types or status
     * conditions. The augment target is the canonical name of either, so one comparison covers both.
     */
    public static Specification<Creature> withAugment(AugmentKind kind, List<String> targets) {
        if (targets == null || targets.isEmpty()) {
            return all();
        }
        List<String> keys = normalize(targets);

        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<CreatureAugment> augment = subquery.from(CreatureAugment.class);
            subquery.select(augment.get("id").get("creatureId")).where(
                    cb.equal(augment.get("id").get("kind"), kind),
                    cb.lower(augment.get("id").get("target")).in(keys));
            return root.get("id").in(subquery);
        };
    }

    /** Matches the creature whose database-generated {@code name_key} equals {@code nameKey}. */
    public static Specification<Creature> withExactName(String nameKey) {
        return (root, query, cb) -> cb.equal(root.<String>get("nameKey"), nameKey);
    }

    private static List<String> normalize(List<String> names) {
        return names.stream().map(name -> name.trim().toLowerCase(Locale.ROOT)).distinct().toList();
    }
}
