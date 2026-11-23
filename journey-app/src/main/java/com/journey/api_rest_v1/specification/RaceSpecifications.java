package com.journey.api_rest_v1.specification;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.journey.api_rest_v1.domain.races.Race;
import com.journey.api_rest_v1.domain.races.RaceSize;
import com.journey.api_rest_v1.domain.races.RaceTraits;
import com.journey.api_rest_v1.domain.traits.Trait;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Reusable JPA Specifications used to build dynamic race filter queries.
 */
public final class RaceSpecifications {

    /** Utility class; not meant to be instantiated. */
    private RaceSpecifications() {
    }

    /** Returns a specification that matches every race; used as the starting point for chaining. */
    public static Specification<Race> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    /** Matches races whose size is any of the given sizes. */
    public static Specification<Race> withSizes(List<RaceSize> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return all();
        }
        return (root, query, cb) -> root.get("raceSize").in(sizes);
    }

    /** Compares movement against a value using lte, lt, eq, gt or gte (defaults to lte). */
    public static Specification<Race> withMovement(String operator, Integer value) {
        if (value == null || value < 0) {
            return all();
        }

        String normalizedOperator = operator == null ? "lte" : operator.trim().toLowerCase(Locale.ROOT);

        return (root, query, cb) -> {
            Path<Integer> field = root.get("movement");
            return switch (normalizedOperator) {
                case "eq" -> cb.equal(field, value);
                case "lt" -> cb.lt(field, value);
                case "gt" -> cb.gt(field, value);
                case "gte" -> cb.greaterThanOrEqualTo(field, value);
                default -> cb.lessThanOrEqualTo(field, value);
            };
        };
    }

    /** Matches races that include at least one trait whose name contains the given text (case-insensitive). */
    public static Specification<Race> withTraitName(String traitName) {
        if (traitName == null || traitName.isBlank()) {
            return all();
        }

        String pattern = "%" + traitName.trim().toLowerCase(Locale.ROOT) + "%";

        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<Race> subRoot = subquery.from(Race.class);
            Join<Race, RaceTraits> link = subRoot.join("traits", JoinType.INNER);
            Join<RaceTraits, Trait> trait = link.join("trait", JoinType.INNER);

            subquery.select(subRoot.get("id")).where(cb.like(trait.get("traitNameKey"), pattern));
            return root.get("id").in(subquery);
        };
    }

    /** Matches the race whose database-generated {@code name_key} equals {@code nameKey}. */
    public static Specification<Race> withExactName(String nameKey) {
        return (root, query, cb) -> cb.equal(root.<String>get("nameKey"), nameKey);
    }
}
