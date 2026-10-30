package com.journey.api_rest_v1.domain.content;
import com.journey.api_rest_v1.domain.powers.Powers;

import java.time.OffsetDateTime;
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
    private java.util.List<Powers> powers;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
