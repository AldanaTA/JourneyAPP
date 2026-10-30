package com.journey.api_rest_v1.domain.powers;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.MapsId;
import jakarta.persistence.Convert;

@Entity
@Table(name = "power_categories")
public class PowerCategories {

    @EmbeddedId
    private PowerCategoriesId id;

    @MapsId("powerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "power_id", nullable = false)
    private Power power;

    @Column(name = "category", nullable = false, insertable = false, updatable = false)
    @Convert(converter = PowerCategoryConverter.class)
    private PowerCategory category;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public PowerCategories(PowerCategoriesId id, Power power, PowerCategory category, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.power = power;
        this.category = category;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public PowerCategories() {
    }

    public PowerCategoriesId getId() {
        return id;
    }

    public void setId(PowerCategoriesId id) {
        this.id = id;
    }

    public Power getPower() {
        return power;
    }

    public void setPower(Power power) {
        this.power = power;
    }

    public PowerCategory getCategory() {
        return category;
    }

    public void setCategory(PowerCategory category) {
        this.category = category;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
