package com.journey.api_rest_v1.domain.entity;

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
    private Powers power;

    @Column(name = "category", nullable = false, insertable = false, updatable = false)
    @Convert(converter = PowerCategoryConverter.class)
    private PowerCategory category;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
