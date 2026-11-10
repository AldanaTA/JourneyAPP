package com.journey.api_rest_v1.domain.powers;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

@Embeddable
public class PowerCategoriesId implements Serializable {

    @Column(name = "power_id", nullable = false, columnDefinition = "uuid")
    private UUID powerId;

    @Convert(converter = PowerCategoryConverter.class)
    @Column(name = "category", nullable = false)
    private PowerCategory category;

    public PowerCategoriesId() {
    }

    public PowerCategoriesId(UUID powerId, PowerCategory category) {
        this.powerId = powerId;
        this.category = category;
    }

    public UUID getPowerId() {
        return powerId;
    }

    public void setPowerId(UUID powerId) {
        this.powerId = powerId;
    }

    public PowerCategory getCategory() {
        return category;
    }

    public void setCategory(PowerCategory category) {
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PowerCategoriesId that = (PowerCategoriesId) o;
        return Objects.equals(powerId, that.powerId) && category == that.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(powerId, category);
    }
}
