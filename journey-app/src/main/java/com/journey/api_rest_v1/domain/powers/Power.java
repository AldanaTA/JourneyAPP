package com.journey.api_rest_v1.domain.powers;
import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.journey.api_rest_v1.domain.content_source.ContentSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "powers") 
public class Power{
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column (name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_key", insertable = false, updatable = false)
    private String nameKey;

    @Column(name = "lvl", nullable = false)
    private Integer lvl;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PowerType type;

    @Column(name = "tp_cost", nullable = false)
    private Integer tpCost;

    @Column(name = "hp_cost")
    private Integer hpCost;

    @Column(name = "mp_cost")
    private Integer mpCost;

    @Column(name = "ep_cost")
    private Integer epCost;

    @Column(name = "material_components")
    private String materialComponents;

    @Column(name = "verbal", nullable = false)
    private Boolean verbal;

    @Column(name = "sight", nullable = false)
    private Boolean sight;

    @Column(name = "somatic", nullable = false)
    private Boolean somatic;

    @Column(name = "is_distinct", nullable = false)
    private Boolean isDistinct;

    @Column(name = "concentration", nullable = false)
    private Boolean concentration;

    @Column(name = "range")
    private String range;

    @Column(name = "area")
    private String area;

    @Column(name = "duration")
    private String duration;

    @Column(name = "effect", nullable = false, columnDefinition = "TEXT")
    private String effect;

    @Column(name = "empower_effect")
    private String empowerEffect;

    @Column(name = "lvl_up_effect")
    private String lvlUpEffect;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "source_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_powers_content_source")
    )
    private ContentSource source;

    @OneToMany(mappedBy = "power")
    private List<PowerCategory> categories;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
