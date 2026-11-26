package com.journey.api_rest_v1.specification;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Limits any content entity with a {@code source} association to a set of content sources. */
public final class SourceSpecifications {

    private SourceSpecifications() {
    }

    /** Content from one of these sources; matches everything when the list is null or empty. */
    public static <T> Specification<T> inSources(List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        List<UUID> ids = sourceIds.stream().filter(Objects::nonNull).map(SourceSpecifications::parse).toList();
        return (root, query, cb) -> root.get("source").get("id").in(ids);
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid source id: " + value);
        }
    }
}
