package com.journey.api_rest_v1.domain.powers;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

@Embeddable
public class PowerCategoriesId implements Serializable {

    @Column(name = "power_id", nullable = false)
    private String powerId;

    @Convert(converter = PowerCategoryConverter.class)
    @Column(name = "category", nullable = false)
    private PowerCategory category;

    public PowerCategoriesId() {
    }

    public PowerCategoriesId(String powerId, PowerCategory category) {
        this.powerId = powerId;
        this.category = category;
    }

    public String getPowerId() {
        return powerId;
    }

    public void setPowerId(String powerId) {
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
