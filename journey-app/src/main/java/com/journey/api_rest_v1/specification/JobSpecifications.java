package com.journey.api_rest_v1.specification;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.journey.api_rest_v1.domain.jobs.Job;
import com.journey.api_rest_v1.domain.jobs.JobCategory;
import com.journey.api_rest_v1.domain.jobs.JobCategoryAssignment;
import com.journey.api_rest_v1.domain.jobs.JobTraits;
import com.journey.api_rest_v1.domain.traits.Trait;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Reusable JPA Specifications used to build dynamic job filter queries.
 */
public final class JobSpecifications {

    /** Utility class; not meant to be instantiated. */
    private JobSpecifications() {
    }

    /** Returns a specification that matches every job; used as the starting point for chaining. */
    public static Specification<Job> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Matches jobs tagged with the given category names (case-insensitive).
     * When {@code matchAll} is true a job must have every category; otherwise any one is enough.
     * When {@code primaryOnly} is true only each job's primary category is considered.
     */
    public static Specification<Job> withCategories(List<String> categories, boolean matchAll, boolean primaryOnly) {
        if (categories == null || categories.isEmpty()) {
            return all();
        }

        List<String> keys = categories.stream()
                .map(category -> category.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<Job> subRoot = subquery.from(Job.class);
            Join<Job, JobCategoryAssignment> assignment = subRoot.join("categories", JoinType.INNER);
            Join<JobCategoryAssignment, JobCategory> category = assignment.join("category", JoinType.INNER);
            Expression<String> categoryKey = cb.lower(category.get("category"));

            Predicate matchesCategory = categoryKey.in(keys);
            if (primaryOnly) {
                matchesCategory = cb.and(matchesCategory, cb.isTrue(assignment.get("primary")));
            }
            subquery.select(subRoot.get("id")).where(matchesCategory);

            if (matchAll) {
                subquery.groupBy(subRoot.get("id"))
                        .having(cb.equal(cb.countDistinct(categoryKey), (long) keys.size()));
            }

            // Using a subquery (instead of joining on the root) avoids duplicate rows and keeps paging counts correct.
            return root.get("id").in(subquery);
        };
    }

    /** Matches jobs that include at least one trait whose name contains the given text (case-insensitive). */
    public static Specification<Job> withTraitName(String traitName) {
        if (traitName == null || traitName.isBlank()) {
            return all();
        }

        String pattern = "%" + traitName.trim().toLowerCase(Locale.ROOT) + "%";

        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<Job> subRoot = subquery.from(Job.class);
            Join<Job, JobTraits> link = subRoot.join("traits", JoinType.INNER);
            Join<JobTraits, Trait> trait = link.join("trait", JoinType.INNER);

            subquery.select(subRoot.get("id")).where(cb.like(trait.get("traitNameKey"), pattern));
            return root.get("id").in(subquery);
        };
    }

    /** Matches the job whose trimmed, lowercased name equals {@code nameKey}. */
    public static Specification<Job> withExactName(String nameKey) {
        return (root, query, cb) -> cb.equal(cb.lower(cb.trim(root.get("jobName"))), nameKey);
    }
}
