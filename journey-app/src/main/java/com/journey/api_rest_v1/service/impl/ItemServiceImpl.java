package com.journey.api_rest_v1.service.impl;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.domain.content_source.ContentSource;
import com.journey.api_rest_v1.domain.items.DamageType;
import com.journey.api_rest_v1.domain.items.Item;
import com.journey.api_rest_v1.domain.items.ItemType;
import com.journey.api_rest_v1.domain.items.ItemWeapon;
import com.journey.api_rest_v1.domain.items.WeaponProperty;
import com.journey.api_rest_v1.domain.items.WeaponType;
import com.journey.api_rest_v1.dto.item.CreateItemRequest;
import com.journey.api_rest_v1.dto.item.CreateItemWeaponRequest;
import com.journey.api_rest_v1.dto.item.ItemFilterRequest;
import com.journey.api_rest_v1.dto.item.ItemReferenceDataDto;
import com.journey.api_rest_v1.dto.item.ItemResponseDto;
import com.journey.api_rest_v1.mapper.ItemMapper;
import com.journey.api_rest_v1.mapper.impl.ItemMapperImpl;
import com.journey.api_rest_v1.repository.ContentSourceRepository;
import com.journey.api_rest_v1.repository.DamageTypeRepository;
import com.journey.api_rest_v1.repository.ItemRepository;
import com.journey.api_rest_v1.repository.WeaponPropertyRepository;
import com.journey.api_rest_v1.repository.WeaponTypeRepository;
import com.journey.api_rest_v1.service.ItemService;
import com.journey.api_rest_v1.specification.ItemSpecifications;

/**
 * Default {@link ItemService} implementation backed by JPA, with Redis caching for reads.
 * Follows the same structure as {@code RaceServiceImpl}.
 *
 * <p>Rules enforced on create/update (the database cannot express them): a derived item keeps its
 * base's item_type, the base chain never loops, a material is a metal item, only weapons have weapon
 * stats/properties, and a weapon with no base has its own stats. Because derived items embed data from
 * their bases, every write evicts all item caches.</p>
 */
@Service
public class ItemServiceImpl implements ItemService {

    private static final String DEFAULT_SORT_FIELD = "name";

    private final ItemRepository itemRepository;
    private final WeaponTypeRepository weaponTypeRepository;
    private final WeaponPropertyRepository weaponPropertyRepository;
    private final DamageTypeRepository damageTypeRepository;
    private final ContentSourceRepository contentSourceRepository;
    private final ItemMapper itemMapper;

    /** Wires the repositories and mapper this service depends on. */
    public ItemServiceImpl(ItemRepository itemRepository,
                           WeaponTypeRepository weaponTypeRepository,
                           WeaponPropertyRepository weaponPropertyRepository,
                           DamageTypeRepository damageTypeRepository,
                           ContentSourceRepository contentSourceRepository,
                           ItemMapper itemMapper) {
        this.itemRepository = itemRepository;
        this.weaponTypeRepository = weaponTypeRepository;
        this.weaponPropertyRepository = weaponPropertyRepository;
        this.damageTypeRepository = damageTypeRepository;
        this.contentSourceRepository = contentSourceRepository;
        this.itemMapper = itemMapper;
    }

    /** Creates an item, rejecting duplicate names with 409 and invalid inheritance with 400. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"itemsById", "itemsAll"}, allEntries = true)
    public ItemResponseDto createItem(CreateItemRequest request) {
        if (itemRepository.existsByNameKey(normalizeName(request.name()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An item named '" + request.name().trim() + "' already exists");
        }

        Item item = new Item();
        applyFields(item, request);
        Item savedItem = itemRepository.save(item);

        // The saved item has its id now, so its weapon row (keyed by item id) can be attached.
        applyWeapon(savedItem, request);

        return itemMapper.CreateResponse(itemRepository.save(savedItem));
    }

    /** Replaces every field of an item, including its weapon stats and properties. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"itemsById", "itemsAll"}, allEntries = true)
    public ItemResponseDto updateItem(String id, CreateItemRequest request) {
        UUID itemId = parseUuid(id, "item");
        Item existingItem = findItem(itemId);

        if (itemRepository.existsByNameKeyAndIdNot(normalizeName(request.name()), itemId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An item named '" + request.name().trim() + "' already exists");
        }
        if (request.item_type() != existingItem.getItemType() && itemRepository.existsByBaseItemId(itemId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot change the item type: other items are based on this one");
        }

        applyFields(existingItem, request);
        applyWeapon(existingItem, request);

        return itemMapper.CreateResponse(itemRepository.save(existingItem));
    }

    /** Deletes an item, refusing with 409 while other items are based on it or made of it. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"itemsById", "itemsAll"}, allEntries = true)
    public String deleteItem(String id) {
        UUID itemId = parseUuid(id, "item");
        Item existingItem = findItem(itemId);

        if (itemRepository.existsByBaseItemId(itemId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete '" + existingItem.getName() + "': other items are based on it");
        }
        if (itemRepository.existsByMaterialId(itemId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete '" + existingItem.getName() + "': other items are made of it");
        }

        String name = existingItem.getName();
        itemRepository.delete(existingItem);
        return "Item with id " + id + " (name: " + name + ") has been deleted.";
    }

    /** Returns one item by id, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "itemsById", key = "#id")
    public ItemResponseDto getItemById(String id) {
        return itemMapper.CreateResponse(findItem(parseUuid(id, "item")));
    }

    /** Returns a sorted page of all items, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "itemsAll", key = "#page + ':' + #size + ':' + #sortBy + ':' + #sortDir")
    public Page<ItemResponseDto> getAllItems(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        return itemRepository.findAll(pageable).map(itemMapper::CreateResponse);
    }

    /** Returns every weapon type, weapon property, and damage type, sorted by name. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "itemReferenceData")
    public ItemReferenceDataDto getReferenceData() {
        Sort byName = Sort.by(Sort.Direction.ASC, "name");
        return new ItemReferenceDataDto(
            weaponTypeRepository.findAll(byName).stream().map(ItemMapperImpl::toWeaponType).toList(),
            weaponPropertyRepository.findAll(byName).stream().map(ItemMapperImpl::toWeaponProperty).toList(),
            damageTypeRepository.findAll(byName).stream().map(ItemMapperImpl::toDamageType).toList()
        );
    }

    /**
     * Applies every filter on the request. When a name is given, an exact match is returned alone;
     * otherwise results are ranked so names containing the search come first, then by edit distance.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ItemResponseDto> filterItems(ItemFilterRequest request) {
        Specification<Item> specification = ItemSpecifications.all()
                .and(ItemSpecifications.withTypes(request.itemTypes()))
                .and(ItemSpecifications.withRarities(request.rarities()))
                .and(ItemSpecifications.magicOnly(request.getMagicOnly()))
                .and(ItemSpecifications.attunementOnly(request.getAttunementOnly()))
                .and(ItemSpecifications.withWeaponTypes(request.weaponTypes()))
                .and(ItemSpecifications.withDamageTypes(request.damageTypes()));

        boolean searchByName = request.name() != null && !request.name().isBlank();
        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                searchByName ? Sort.unsorted() : Sort.by(Sort.Direction.ASC, DEFAULT_SORT_FIELD));

        if (!searchByName) {
            return itemRepository.findAll(specification, pageable).map(itemMapper::CreateResponse);
        }

        String searchName = normalizeName(request.name());
        List<Item> exactMatches = itemRepository
                .findAll(specification.and(ItemSpecifications.withExactName(searchName)), PageRequest.of(0, 1))
                .getContent();
        if (!exactMatches.isEmpty()) {
            ItemResponseDto exactMatch = itemMapper.CreateResponse(exactMatches.get(0));
            return new PageImpl<>(List.of(exactMatch), PageRequest.of(0, request.getSize()), 1);
        }

        List<Item> rankedItems = itemRepository.findAll(specification).stream()
                .sorted(Comparator
                        .comparingInt((Item item) ->
                                item.getName().toLowerCase(Locale.ROOT).contains(searchName) ? 0 : 1)
                        .thenComparingInt(item -> levenshteinDistance(
                                searchName, item.getName().toLowerCase(Locale.ROOT)))
                        .thenComparing(Item::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int start = (int) Math.min(pageable.getOffset(), rankedItems.size());
        int end = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), rankedItems.size());
        List<ItemResponseDto> pageContent = rankedItems.subList(start, end).stream()
                .map(itemMapper::CreateResponse)
                .toList();
        return new PageImpl<>(pageContent, pageable, rankedItems.size());
    }

    /** Copies the request's plain fields, base item, and material onto the item, validating inheritance. */
    private void applyFields(Item item, CreateItemRequest request) {
        ContentSource contentSource = findContentSource(request.content_source_id());
        Item baseItem = resolveBaseItem(item, request);
        Item material = resolveMaterial(item, request.material_id());

        item.setName(request.name().trim());
        item.setItemType(request.item_type());
        item.setBaseItem(baseItem);
        item.setMaterial(material);
        item.setMagic(Boolean.TRUE.equals(request.is_magic()));
        item.setRarity(request.rarity());
        item.setDescription(blankToNull(request.description()));
        item.setEffect(blankToNull(request.effect()));
        item.setCostBronze(request.cost_bronze());
        item.setStackLimit(request.stack_limit() == null ? 1 : request.stack_limit());
        item.setBundleQuantity(request.bundle_quantity() == null ? 1 : request.bundle_quantity());
        item.setRequiresAttunement(Boolean.TRUE.equals(request.requires_attunement()));
        item.setCharges(request.charges());
        item.setDetails(request.details() == null ? new HashMap<>() : new HashMap<>(request.details()));
        item.setSource(contentSource);
    }

    /**
     * Sets or clears the item's own weapon stats and properties.
     * Only weapons may have them; a weapon without a base must have its own stats.
     */
    private void applyWeapon(Item item, CreateItemRequest request) {
        CreateItemWeaponRequest weaponRequest = request.weapon();
        boolean hasProperties = request.weapon_properties() != null && !request.weapon_properties().isEmpty();

        if (item.getItemType() != ItemType.WEAPON) {
            if (weaponRequest != null || hasProperties) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Only weapons can have weapon stats or weapon properties");
            }
            item.setWeapon(null);
            item.getWeaponProperties().clear();
            return;
        }

        if (weaponRequest == null && item.getBaseItem() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A weapon that is not based on another item needs weapon stats");
        }

        if (weaponRequest == null) {
            item.setWeapon(null);
        } else {
            WeaponType weaponType = findWeaponType(weaponRequest.weapon_type());
            DamageType damageType = findDamageType(weaponRequest.damage_type());
            String damageDice = weaponRequest.damage_dice().trim().toLowerCase(Locale.ROOT);
            String damageBonus = blankToNull(weaponRequest.damage_bonus());

            ItemWeapon weapon = item.getWeapon();
            if (weapon == null) {
                item.setWeapon(new ItemWeapon(item, weaponType, damageDice, damageBonus, damageType,
                        weaponRequest.range_ft(), weaponRequest.mag()));
            } else {
                weapon.setWeaponType(weaponType);
                weapon.setDamageDice(damageDice);
                weapon.setDamageBonus(damageBonus);
                weapon.setDamageType(damageType);
                weapon.setRangeFt(weaponRequest.range_ft());
                weapon.setMag(weaponRequest.mag());
            }
        }

        // null/empty inherits the base's properties (or means none for a weapon without a base).
        Set<WeaponProperty> properties = new LinkedHashSet<>();
        if (hasProperties) {
            for (String name : request.weapon_properties()) {
                properties.add(findWeaponProperty(name));
            }
        }
        item.getWeaponProperties().clear();
        item.getWeaponProperties().addAll(properties);
    }

    /** Loads the requested base item, enforcing the same item_type and no loops in the base chain. */
    private Item resolveBaseItem(Item item, CreateItemRequest request) {
        if (request.base_item_id() == null || request.base_item_id().isBlank()) {
            return null;
        }

        UUID baseId = parseUuid(request.base_item_id(), "base item");
        Item baseItem = itemRepository.findById(baseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Base item not found with id: " + request.base_item_id()));

        if (baseItem.getItemType() != request.item_type()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An item must have the same item type as its base ('" + baseItem.getName() + "' is "
                            + baseItem.getItemType() + ")");
        }

        Set<UUID> seen = new HashSet<>();
        for (Item current = baseItem; current != null; current = current.getBaseItem()) {
            if (current.getId().equals(item.getId()) || !seen.add(current.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "An item cannot be based on itself, directly or through its base items");
            }
        }

        return baseItem;
    }

    /** Loads the requested material, which must be a metal item other than the item itself. */
    private Item resolveMaterial(Item item, String materialId) {
        if (materialId == null || materialId.isBlank()) {
            return null;
        }

        UUID id = parseUuid(materialId, "material");
        Item material = itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Material not found with id: " + materialId));

        if (material.getItemType() != ItemType.METAL || id.equals(item.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A material must be a metal item ('" + material.getName() + "' is " + material.getItemType() + ")");
        }
        return material;
    }

    /** Finds a weapon type by name or throws 400 listing the valid names. */
    private WeaponType findWeaponType(String name) {
        return weaponTypeRepository.findByNameKey(normalizeName(name))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown weapon type '" + name + "'. Valid: "
                                + weaponTypeRepository.findAll().stream().map(WeaponType::getName).sorted().toList()));
    }

    /** Finds a damage type by name or throws 400 listing the valid names. */
    private DamageType findDamageType(String name) {
        return damageTypeRepository.findByNameKey(normalizeName(name))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown damage type '" + name + "'. Valid: "
                                + damageTypeRepository.findAll().stream().map(DamageType::getName).sorted().toList()));
    }

    /** Finds a weapon property by name or throws 400 listing the valid names. */
    private WeaponProperty findWeaponProperty(String name) {
        return weaponPropertyRepository.findByNameKey(normalizeName(name))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown weapon property '" + name + "'. Valid: "
                                + weaponPropertyRepository.findAll().stream().map(WeaponProperty::getName).sorted().toList()));
    }

    /** Loads an item or throws 404 when it does not exist. */
    private Item findItem(UUID itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Item not found with id: " + itemId));
    }

    /** Loads a content source or throws 404 when it does not exist. */
    private ContentSource findContentSource(String contentSourceId) {
        UUID sourceId = parseUuid(contentSourceId, "content source");
        return contentSourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Content source not found with id: " + contentSourceId));
    }

    /** Parses a UUID string, turning malformed input into a 400 instead of a 500. */
    private static UUID parseUuid(String value, String label) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + label + " id: " + value);
        }
    }

    /** Normalizes a name the same way the database computes {@code name_key} (trimmed, lowercase). */
    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** Returns the trimmed text, or null when blank. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Computes the Levenshtein edit distance between two strings, used to rank fuzzy name matches. */
    private static int levenshteinDistance(String first, String second) {
        String columns = first.length() <= second.length() ? first : second;
        String rows = first.length() <= second.length() ? second : first;
        int[] previous = new int[columns.length() + 1];
        int[] current = new int[columns.length() + 1];

        for (int column = 0; column <= columns.length(); column++) {
            previous[column] = column;
        }

        for (int row = 1; row <= rows.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= columns.length(); column++) {
                int substitutionCost = rows.charAt(row - 1) == columns.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + substitutionCost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[columns.length()];
    }
}
