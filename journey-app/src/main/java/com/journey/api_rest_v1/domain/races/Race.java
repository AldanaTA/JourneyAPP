package com.journey.api_rest_v1.domain.races;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.journey.api_rest_v1.domain.content_source.ContentSource;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code races} table.
 * A race is a playable heritage with a size, movement speed, languages,
 * and an ordered list of racial traits (regular traits tagged {@code racial}).
 */
@Entity
@Table(name = "races")
public class Race {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "race_name", nullable = false)
    private String raceName;

    @Column(name = "name_key", insertable = false, updatable = false)
    private String nameKey;

    // Stored in the Postgres "size" enum; the JDBC stringtype=unspecified setting lets the label bind to it.
    @Enumerated(EnumType.STRING)
    @Column(name = "min_race_size", nullable = false)
    private RaceSize minRaceSize;

    // Upper end of a size range (e.g. S - M); null when the race has a single size.
    @Enumerated(EnumType.STRING)
    @Column(name = "max_race_size")
    private RaceSize maxRaceSize;

    @Column(name = "movement", nullable = false)
    private Integer movement;

    @Column(name = "languages", nullable = false, columnDefinition = "TEXT")
    private String languages;

    @Column(name = "race_description", columnDefinition = "TEXT")
    private String raceDescription;

    // Batch-loaded so a page of races does not issue one query per race.
    @BatchSize(size = 50)
    @OrderBy("sortOrder ASC")
    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RaceTraits> traits = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "source_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_races_content_source")
    )
    private ContentSource source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public Race() {
    }

    /** Creates a fully populated race; a null trait list is replaced with an empty one. */
    public Race(UUID id, String raceName, RaceSize minRaceSize, RaceSize maxRaceSize, Integer movement,
            String languages,
            String raceDescription, List<RaceTraits> traits, ContentSource source, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.raceName = raceName;
        this.minRaceSize = minRaceSize;
        this.maxRaceSize = maxRaceSize;
        this.movement = movement;
        this.languages = languages;
        this.raceDescription = raceDescription;
        this.traits = traits == null ? new ArrayList<>() : traits;
        this.source = source;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Returns the race's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Sets the race's UUID primary key. */
    public void setId(UUID id) {
        this.id = id;
    }

    /** Returns the display name of the race. */
    public String getRaceName() {
        return raceName;
    }

    /** Sets the display name of the race. */
    public void setRaceName(String raceName) {
        this.raceName = raceName;
    }

    /** Returns the database-generated lowercase/trimmed name used for uniqueness. */
    public String getNameKey() {
        return nameKey;
    }

    /** Returns the race's size, or the smallest size of its range. */
    public RaceSize getMinRaceSize() {
        return minRaceSize;
    }

    /** Sets the race's size, or the smallest size of its range. */
    public void setMinRaceSize(RaceSize minRaceSize) {
        this.minRaceSize = minRaceSize;
    }

    /** Returns the largest size of the race's range, or null when it has a single size. */
    public RaceSize getMaxRaceSize() {
        return maxRaceSize;
    }

    /** Sets the largest size of the race's range; null means the race has a single size. */
    public void setMaxRaceSize(RaceSize maxRaceSize) {
        this.maxRaceSize = maxRaceSize;
    }

    /** Returns the race's base movement in feet. */
    public Integer getMovement() {
        return movement;
    }

    /** Sets the race's base movement in feet. */
    public void setMovement(Integer movement) {
        this.movement = movement;
    }

    /** Returns the languages the race knows, as written (e.g. "Common and Dwarvish"). */
    public String getLanguages() {
        return languages;
    }

    /** Sets the languages the race knows. */
    public void setLanguages(String languages) {
        this.languages = languages;
    }

    /** Returns the flavor description of the race. */
    public String getRaceDescription() {
        return raceDescription;
    }

    /** Sets the flavor description of the race. */
    public void setRaceDescription(String raceDescription) {
        this.raceDescription = raceDescription;
    }

    /** Returns the trait links attached to this race, ordered by sort order. */
    public List<RaceTraits> getTraits() {
        return traits;
    }

    /** Replaces the trait links; a null list is replaced with an empty one. */
    public void setTraits(List<RaceTraits> traits) {
        this.traits = traits == null ? new ArrayList<>() : traits;
    }

    /** Returns the content source (book/expansion) this race belongs to. */
    public ContentSource getSource() {
        return source;
    }

    /** Sets the content source (book/expansion) this race belongs to. */
    public void setSource(ContentSource source) {
        this.source = source;
    }

    /** Returns when the race was created. */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** Returns when the race was last updated. */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** Sets when the race was last updated. */
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
