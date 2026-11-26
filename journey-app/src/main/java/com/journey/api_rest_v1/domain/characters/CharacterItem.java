package com.journey.api_rest_v1.domain.characters;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code character_items} table: one stack of items a character carries and where.
 */
@Entity
@Table(name = "character_items")
public class CharacterItem {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "character_id", nullable = false, columnDefinition = "uuid")
    private UUID characterId;

    @Column(name = "item_id", columnDefinition = "uuid")
    private UUID itemId;

    @Column(name = "custom_name", columnDefinition = "TEXT")
    private String customName;

    @Convert(converter = InventoryLocationConverter.class)
    @Column(name = "location", nullable = false)
    private InventoryLocation location;

    @Column(name = "slot_id", columnDefinition = "uuid")
    private UUID slotId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    /** This stack's own limit; null = the item's stack limit x the character's multiplier. */
    @Column(name = "stack_limit")
    private Integer stackLimit;

    @Column(name = "attuned", nullable = false)
    private boolean attuned;

    @Column(name = "charges_remaining")
    private Integer chargesRemaining;

    @Column(name = "notes", nullable = false, columnDefinition = "TEXT")
    private String notes = "";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public CharacterItem() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public void setCharacterId(UUID characterId) {
        this.characterId = characterId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public void setItemId(UUID itemId) {
        this.itemId = itemId;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public InventoryLocation getLocation() {
        return location;
    }

    public void setLocation(InventoryLocation location) {
        this.location = location;
    }

    public UUID getSlotId() {
        return slotId;
    }

    public void setSlotId(UUID slotId) {
        this.slotId = slotId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public boolean isAttuned() {
        return attuned;
    }

    public void setAttuned(boolean attuned) {
        this.attuned = attuned;
    }

    public Integer getChargesRemaining() {
        return chargesRemaining;
    }

    public void setChargesRemaining(Integer chargesRemaining) {
        this.chargesRemaining = chargesRemaining;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Integer getStackLimit() {
        return stackLimit;
    }

    public void setStackLimit(Integer stackLimit) {
        this.stackLimit = stackLimit;
    }
}
