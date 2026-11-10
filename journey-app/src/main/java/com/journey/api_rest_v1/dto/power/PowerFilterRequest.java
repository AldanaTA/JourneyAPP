package com.journey.api_rest_v1.dto.power;

import java.util.List;

import com.journey.api_rest_v1.domain.powers.PowerCategory;

import jakarta.validation.constraints.NotEmpty;

public record PowerFilterRequest(
    @NotEmpty(message = "categories must not be empty") List<PowerCategory> categories,
    Boolean matchAll,
    Integer page,
    Integer size
) {
    public boolean getMatchAll() {
        return matchAll != null && matchAll;
    }

    public int getPage() {
        return page != null ? page : 0;
    }

    public int getSize() {
        return size != null ? size : 5;
    }
}
