package com.journey.api_rest_v1.specification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.powers.PowerCategories;
import com.journey.api_rest_v1.domain.powers.PowerCategory;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class PowerSpecifications {

    private PowerSpecifications() {
    }

    public static Specification<Power> withCategories(List<PowerCategory> categories, boolean matchAll) {
        if (categories == null || categories.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }

        return (root, query, cb) -> {
            query.distinct(true);

            if (matchAll) {
                Subquery<UUID> subquery = query.subquery(UUID.class);
                Root<Power> subRoot = subquery.from(Power.class);
                Join<Power, PowerCategories> join = subRoot.join("categories", JoinType.INNER);
                Path<PowerCategory> categoryPath = join.get("id").get("category");

                subquery.select(subRoot.get("id"))
                        .where(categoryPath.in(categories))
                        .groupBy(subRoot.get("id"))
                        .having(cb.equal(cb.countDistinct(categoryPath), (long) categories.size()));

                return root.get("id").in(subquery);
            }

            Join<Power, PowerCategories> join = root.join("categories", JoinType.INNER);
            Path<PowerCategory> categoryPath = join.get("id").get("category");
            return categoryPath.in(categories);
        };
    }

    public static Specification<Power> withResourceFilter(String fieldName, String operator, Integer value) {
        if (fieldName == null || fieldName.isBlank() || value == null) {
            return (root, query, cb) -> cb.conjunction();
        }

        String normalizedField = fieldName.trim();
        String normalizedOperator = operator == null ? "lte" : operator.trim().toLowerCase();

        return (root, query, cb) -> {
            Path<Integer> field = root.get(normalizedField);
            Predicate nonNull = cb.isNotNull(field);

            Predicate comparison = switch (normalizedOperator) {
                case "eq" -> cb.equal(field, value);
                case "lt" -> cb.lt(field, value);
                case "lte" -> cb.lessThanOrEqualTo(field, value);
                case "gt" -> cb.gt(field, value);
                case "gte" -> cb.greaterThanOrEqualTo(field, value);
                default -> cb.lessThanOrEqualTo(field, value);
            };

            return cb.and(nonNull, comparison);
        };
    }
}
