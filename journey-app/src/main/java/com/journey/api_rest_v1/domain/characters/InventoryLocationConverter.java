package com.journey.api_rest_v1.domain.characters;

import com.journey.api_rest_v1.domain.common.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** Converts {@link InventoryLocation} to and from the lowercase labels of the Postgres {@code inventory_location} enum. */
@Converter(autoApply = false)
public class InventoryLocationConverter extends LowercaseEnumConverter<InventoryLocation> {

    /** Creates the converter. */
    public InventoryLocationConverter() {
        super(InventoryLocation.class);
    }
}
