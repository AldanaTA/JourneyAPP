package com.journey.api_rest_v1.dto.item;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** Weapon types, weapon properties, and damage types, for building item filters and forms. */
public record ItemReferenceDataDto(
    List<WeaponTypeDto> weaponTypes,
    List<WeaponPropertyDto> weaponProperties,
    List<DamageTypeDto> damageTypes
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
