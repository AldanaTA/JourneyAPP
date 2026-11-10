package com.journey.api_rest_v1.dto.power;

public record ResourceFilter(
    String field,
    String operator,
    Integer value
) {
    public String field() {
        return field == null ? "" : field.trim();
    }

    public String operator() {
        return operator == null ? "lte" : operator.trim().toLowerCase();
    }
}
