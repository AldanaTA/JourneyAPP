package com.journey.api_rest_v1.domain.content_source;
import com.journey.api_rest_v1.domain.powers.Power;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "content_sources")
public class ContentSource {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "source_name", nullable = false)
    private String source_name;

    @Column (name = "source_desc", length = 1000, nullable = false)
    private String source_desc;

    @OneToMany(mappedBy = "source")
    private java.util.List<Power> powers;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public ContentSource(UUID id, String source_name, String source_desc, List<Power> powers,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.source_name = source_name;
        this.source_desc = source_desc;
        this.powers = powers;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public ContentSource() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSource_name() {
        return source_name;
    }

    public void setSource_name(String source_name) {
        this.source_name = source_name;
    }

    public String getSource_desc() {
        return source_desc;
    }

    public void setSource_desc(String source_desc) {
        this.source_desc = source_desc;
    }

    public java.util.List<Power> getPowers() {
        return powers;
    }

    public void setPowers(java.util.List<Power> powers) {
        this.powers = powers;
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
