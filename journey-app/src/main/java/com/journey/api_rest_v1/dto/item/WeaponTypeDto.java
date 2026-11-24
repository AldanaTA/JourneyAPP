package com.journey.api_rest_v1.dto.item;

import java.io.Serial;
import java.io.Serializable;

/** A weapon type and the effect every weapon of that type grants (e.g. Bow -> Nimble Shot). */
public record WeaponTypeDto(String name, String effectName, String effect) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
