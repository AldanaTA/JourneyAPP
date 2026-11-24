package com.journey.api_rest_v1.domain.items;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import com.journey.api_rest_v1.domain.content_source.ContentSource;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * JPA entity for a row in the {@code items} table.
 *
 * <p>Inheritance: an item may be based on another ({@link #getBaseItem()}). Name, rarity, cost,
 * effect, attunement, charges and the magic flag are always the item's own. Weapon stats, weapon
 * properties and material are inherited from the base unless the item sets its own, and
 * {@code details} is merged over the base's. Resolution happens in the item mapper.</p>
 */
@Entity
@Table(name = "items")
public class Item {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_key", insertable = false, updatable = false)
    private String nameKey;

    // Stored in the Postgres "item_type" enum; the JDBC stringtype=unspecified setting lets the label bind to it.
    @Convert(converter = ItemTypeConverter.class)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_item_id", foreignKey = @ForeignKey(name = "items_base_item_id_fkey"))
    private Item baseItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", foreignKey = @ForeignKey(name = "items_material_id_fkey"))
    private Item material;

    @Column(name = "is_magic", nullable = false)
    private boolean magic;

    @Convert(converter = ItemRarityConverter.class)
    @Column(name = "rarity", nullable = false)
    private ItemRarity rarity;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "effect", columnDefinition = "TEXT")
    private String effect;

    @Column(name = "cost_bronze")
    private Integer costBronze;

    @Column(name = "stack_limit", nullable = false)
    private Integer stackLimit = 1;

    @Column(name = "bundle_quantity", nullable = false)
    private Integer bundleQuantity = 1;

    @Column(name = "requires_attunement", nullable = false)
    private boolean requiresAttunement;

    @Column(name = "charges")
    private Integer charges;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details = new HashMap<>();

    @OneToOne(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private ItemWeapon weapon;

    @ManyToMany
    @JoinTable(
        name = "item_weapon_properties",
        joinColumns = @JoinColumn(name = "item_id"),
        inverseJoinColumns = @JoinColumn(name = "property_id")
    )
    private Set<WeaponProperty> weaponProperties = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "source_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_items_content_source")
    )
    private ContentSource source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** No-args constructor required by JPA. */
    public Item() {
    }

    /** Returns the item's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Returns the display name of the item. */
    public String getName() {
        return name;
    }

    /** Sets the display name of the item. */
    public void setName(String name) {
        this.name = name;
    }

    /** Returns the database-generated lowercase/trimmed name used for uniqueness. */
    public String getNameKey() {
        return nameKey;
    }

    /** Returns what the item is (weapon, potion, ...). */
    public ItemType getItemType() {
        return itemType;
    }

    /** Sets what the item is. */
    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    /** Returns the item this one is based on, or null. */
    public Item getBaseItem() {
        return baseItem;
    }

    /** Sets the item this one is based on. */
    public void setBaseItem(Item baseItem) {
        this.baseItem = baseItem;
    }

    /** Returns the metal item this one is made of (not resolved through the base), or null. */
    public Item getMaterial() {
        return material;
    }

    /** Sets the metal item this one is made of. */
    public void setMaterial(Item material) {
        this.material = material;
    }

    /** Returns true for magic items. */
    public boolean isMagic() {
        return magic;
    }

    /** Marks the item as magic or not. */
    public void setMagic(boolean magic) {
        this.magic = magic;
    }

    /** Returns the item's rarity. */
    public ItemRarity getRarity() {
        return rarity;
    }

    /** Sets the item's rarity. */
    public void setRarity(ItemRarity rarity) {
        this.rarity = rarity;
    }

    /** Returns the flavor description, or null. */
    public String getDescription() {
        return description;
    }

    /** Sets the flavor description. */
    public void setDescription(String description) {
        this.description = description;
    }

    /** Returns the rules text of the item's effect, or null. */
    public String getEffect() {
        return effect;
    }

    /** Sets the rules text of the item's effect. */
    public void setEffect(String effect) {
        this.effect = effect;
    }

    /** Returns the price in bronze, or null when the item has no listed price. */
    public Integer getCostBronze() {
        return costBronze;
    }

    /** Sets the price in bronze. */
    public void setCostBronze(Integer costBronze) {
        this.costBronze = costBronze;
    }

    /** Returns how many units fit in one inventory slot. */
    public Integer getStackLimit() {
        return stackLimit;
    }

    /** Sets how many units fit in one inventory slot. */
    public void setStackLimit(Integer stackLimit) {
        this.stackLimit = stackLimit;
    }

    /** Returns how many units are bought for the listed cost. */
    public Integer getBundleQuantity() {
        return bundleQuantity;
    }

    /** Sets how many units are bought for the listed cost. */
    public void setBundleQuantity(Integer bundleQuantity) {
        this.bundleQuantity = bundleQuantity;
    }

    /** Returns true when the item must be attuned to be used. */
    public boolean isRequiresAttunement() {
        return requiresAttunement;
    }

    /** Sets whether the item must be attuned to be used. */
    public void setRequiresAttunement(boolean requiresAttunement) {
        this.requiresAttunement = requiresAttunement;
    }

    /** Returns the number of charges, or null for passive / no charges. */
    public Integer getCharges() {
        return charges;
    }

    /** Sets the number of charges (null for passive / no charges). */
    public void setCharges(Integer charges) {
        this.charges = charges;
    }

    /** Returns this item's own type-specific details (not merged with its base). */
    public Map<String, Object> getDetails() {
        return details;
    }

    /** Replaces the type-specific details; null becomes an empty map. */
    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? new HashMap<>() : details;
    }

    /** Returns this item's own weapon stats (not resolved through the base), or null. */
    public ItemWeapon getWeapon() {
        return weapon;
    }

    /** Sets this item's own weapon stats; null removes them. */
    public void setWeapon(ItemWeapon weapon) {
        this.weapon = weapon;
    }

    /** Returns this item's own weapon properties (not resolved through the base). */
    public Set<WeaponProperty> getWeaponProperties() {
        return weaponProperties;
    }

    /** Returns the content source (book/expansion) this item belongs to. */
    public ContentSource getSource() {
        return source;
    }

    /** Sets the content source (book/expansion) this item belongs to. */
    public void setSource(ContentSource source) {
        this.source = source;
    }

    /** Returns when the item was created. */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** Returns when the item was last updated. */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
