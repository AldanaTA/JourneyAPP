package com.journey.api_rest_v1.domain.content_source;
import com.journey.api_rest_v1.domain.powers.Power;

import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "content_source")
public class ContentSource {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column (name = "id", nullable = false, updatable = false)
    private String id;

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

    public ContentSource(String id, String source_name, String source_desc, List<Power> powers,
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
}
