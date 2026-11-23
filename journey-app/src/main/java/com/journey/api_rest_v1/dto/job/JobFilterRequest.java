package com.journey.api_rest_v1.dto.job;

import java.util.List;

/**
 * Request body for the job filter endpoint.
 * Every field is optional; null values are ignored so the client only sends what the user set.
 */
public record JobFilterRequest(
    List<String> categories,
    Boolean matchAll,
    Boolean primaryOnly,
    Integer page,
    Integer size,
    String name,
    String traitName
) {
    /** Returns true only when the client explicitly asked for jobs matching every selected category. */
    public boolean getMatchAll() {
        return matchAll != null && matchAll;
    }

    /** Returns true only when the client asked to match against each job's primary category alone. */
    public boolean getPrimaryOnly() {
        return primaryOnly != null && primaryOnly;
    }

    /** Returns the requested page, defaulting to the first page. */
    public int getPage() {
        return page != null && page >= 0 ? page : 0;
    }

    /** Returns the requested page size, defaulting to 5 like the trait filter. */
    public int getSize() {
        return size != null && size > 0 ? size : 5;
    }
}
