package com.journey.api_rest_v1.specification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.domain.traits.TraitCategories;
import com.journey.api_rest_v1.domain.traits.TraitCategory;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Reusable JPA Specifications used to build dynamic trait filter queries.
 */
public final class TraitSpecifications {

    /** Utility class; not meant to be instantiated. */
    private TraitSpecifications() {
    }

    /** Returns a specification that matches every trait; used as the starting point for chaining. */
    public static Specification<Trait> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Matches traits tagged with the given categories.
     * When {@code matchAll} is true a trait must have every category; otherwise any one is enough.
     */
    public static Specification<Trait> withCategories(List<TraitCategory> categories, boolean matchAll) {
        if (categories == null || categories.isEmpty()) {
            return all();
        }

        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<Trait> subRoot = subquery.from(Trait.class);
            Join<Trait, TraitCategories> join = subRoot.join("categories", JoinType.INNER);
            Path<TraitCategory> categoryPath = join.get("id").get("category");

            subquery.select(subRoot.get("id")).where(categoryPath.in(categories));

            if (matchAll) {
                subquery.groupBy(subRoot.get("id"))
                        .having(cb.equal(cb.countDistinct(categoryPath), (long) categories.size()));
            }

            // Using a subquery (instead of joining on the root) avoids duplicate rows and keeps paging counts correct.
            return root.get("id").in(subquery);
        };
    }

    /**
     * Compares a numeric trait column against a value using lte, lt, eq, gt or gte (defaults to lte).
     * Null columns never match, so a filter on a cost only returns traits that define that cost.
     */
    public static Specification<Trait> withNumericFilter(String fieldName, String operator, Integer value) {
        if (fieldName == null || fieldName.isBlank() || value == null) {
            return all();
        }

        String normalizedField = fieldName.trim();
        String normalizedOperator = operator == null ? "lte" : operator.trim().toLowerCase();

        return (root, query, cb) -> {
            Path<Integer> field = root.get(normalizedField);
            Predicate nonNull = cb.isNotNull(field);

            Predicate comparison = switch (normalizedOperator) {
                case "eq" -> cb.equal(field, value);
                case "lt" -> cb.lt(field, value);
                case "gt" -> cb.gt(field, value);
                case "gte" -> cb.greaterThanOrEqualTo(field, value);
                default -> cb.lessThanOrEqualTo(field, value);
            };

            return cb.and(nonNull, comparison);
        };
    }
}
