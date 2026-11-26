package com.journey.api_rest_v1.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.domain.characters.CharacterAttunementSlot;
import com.journey.api_rest_v1.domain.characters.CharacterInventory;
import com.journey.api_rest_v1.domain.characters.CharacterInventorySlot;
import com.journey.api_rest_v1.domain.characters.CharacterItem;
import com.journey.api_rest_v1.domain.characters.InventoryLocation;
import com.journey.api_rest_v1.domain.items.Item;
import com.journey.api_rest_v1.dto.character.AttunementSlotDto;
import com.journey.api_rest_v1.dto.character.CharacterInventoryDto;
import com.journey.api_rest_v1.dto.character.CharacterItemDto;
import com.journey.api_rest_v1.dto.character.InventorySlotDto;
import com.journey.api_rest_v1.dto.character.SaveCharacterItemRequest;
import com.journey.api_rest_v1.dto.character.SaveInventorySlotRequest;
import com.journey.api_rest_v1.dto.character.UpdateInventoryRequest;
import com.journey.api_rest_v1.repository.CharacterAttunementSlotRepository;
import com.journey.api_rest_v1.repository.CharacterInventoryRepository;
import com.journey.api_rest_v1.repository.CharacterInventorySlotRepository;
import com.journey.api_rest_v1.repository.CharacterItemRepository;
import com.journey.api_rest_v1.repository.ItemRepository;
import com.journey.api_rest_v1.service.CharacterInventoryService;

/**
 * Default {@link CharacterInventoryService}.
 *
 * <p>The database keeps one item in armor, the bag, and each hand (a two-handed item takes both), one stack per
 * slot, and slots and items on the same character; conflicts come back as 409 with a readable message. This
 * service adds the rules that depend on the compendium item and the character's modifiers:</p>
 * <ul>
 *   <li>Stack limit = the stack's own limit if set, else item stack limit x the character's multiplier.</li>
 *   <li>Attunement only for items that need it, and attuned items within the effective limit
 *       (base limit + one per attunement slot).</li>
 * </ul>
 */
@Service
public class CharacterInventoryServiceImpl implements CharacterInventoryService {

    private final CharacterInventoryRepository inventoryRepository;
    private final CharacterInventorySlotRepository slotRepository;
    private final CharacterAttunementSlotRepository attunementSlotRepository;
    private final CharacterItemRepository itemRepository;
    private final ItemRepository compendiumItems;
    private final CharacterAccess access;

    /** Wires the repositories and the ownership helper. */
    public CharacterInventoryServiceImpl(CharacterInventoryRepository inventoryRepository,
            CharacterInventorySlotRepository slotRepository,
            CharacterAttunementSlotRepository attunementSlotRepository,
            CharacterItemRepository itemRepository,
            ItemRepository compendiumItems,
            CharacterAccess access) {
        this.inventoryRepository = inventoryRepository;
        this.slotRepository = slotRepository;
        this.attunementSlotRepository = attunementSlotRepository;
        this.itemRepository = itemRepository;
        this.compendiumItems = compendiumItems;
        this.access = access;
    }

    @Override
    @Transactional
    public CharacterInventoryDto get(String characterId) {
        return toDto(access.loadOwned(characterId).getId());
    }

    @Override
    @Transactional
    public CharacterInventoryDto update(String characterId, UpdateInventoryRequest r) {
        UUID id = access.loadOwned(characterId).getId();
        CharacterInventory inventory = inventory(id);
        if (r.platinum() != null) inventory.setPlatinum(r.platinum());
        if (r.gold() != null) inventory.setGold(r.gold());
        if (r.silver() != null) inventory.setSilver(r.silver());
        if (r.bronze() != null) inventory.setBronze(r.bronze());
        if (r.attunement_limit() != null) inventory.setAttunementLimit(r.attunement_limit());
        if (r.stack_limit_multiplier() != null) inventory.setStackLimitMultiplier(r.stack_limit_multiplier());
        if (r.notes() != null) inventory.setNotes(r.notes());
        inventoryRepository.saveAndFlush(inventory);
        requireAttunementsWithinLimit(id, "Lowering the limit");
        return toDto(id);
    }

    // ----------------------------
    // Inventory slots
    // ----------------------------

    @Override
    @Transactional
    public CharacterInventoryDto addSlots(String characterId, SaveInventorySlotRequest r) {
        UUID id = access.loadOwned(characterId).getId();
        String group = blankToNull(r.group_name());
        List<String> existing = group == null ? List.of()
                : slotRepository.findByCharacterIdAndGroupName(id, group).stream().map(CharacterInventorySlot::getLabel).toList();
        int order = slotRepository.findByCharacterIdOrderBySortOrderAscCreatedAtAsc(id).size();
        for (String label : newLabels(r, group, existing)) {
            CharacterInventorySlot slot = new CharacterInventorySlot();
            slot.setCharacterId(id);
            slot.setLabel(label);
            slot.setGroupName(group);
            slot.setNotes(r.notes() == null ? "" : r.notes());
            slot.setSortOrder(r.sort_order() == null ? order++ : r.sort_order());
            slotRepository.save(slot);
        }
        slotRepository.flush();
        return toDto(id);
    }

    @Override
    @Transactional
    public CharacterInventoryDto updateSlot(String characterId, String slotId, SaveInventorySlotRequest r) {
        UUID id = access.loadOwned(characterId).getId();
        CharacterInventorySlot slot = slot(id, slotId);
        if (r.label() != null) slot.setLabel(r.label().trim());
        if (r.notes() != null) slot.setNotes(r.notes());
        if (r.sort_order() != null) slot.setSortOrder(r.sort_order());
        slotRepository.saveAndFlush(slot);
        return toDto(id);
    }

    @Override
    @Transactional
    public CharacterInventoryDto deleteSlot(String characterId, String slotId) {
        UUID id = access.loadOwned(characterId).getId();
        CharacterInventorySlot slot = slot(id, slotId);
        if (itemRepository.existsBySlotId(slot.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Move or remove the item in this slot first");
        }
        slotRepository.delete(slot);
        slotRepository.flush();
        return toDto(id);
    }

    @Override
    @Transactional
    public CharacterInventoryDto deleteSlotGroup(String characterId, String groupName) {
        UUID id = access.loadOwned(characterId).getId();
        List<CharacterInventorySlot> slots = slotRepository.findByCharacterIdAndGroupName(id, requireGroup(groupName));
        if (slots.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No slots in the group " + groupName.trim());
        }
        List<String> occupied = slots.stream().filter(s -> itemRepository.existsBySlotId(s.getId()))
                .map(CharacterInventorySlot::getLabel).toList();
        if (!occupied.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Move or remove the items in " + String.join(", ", occupied) + " first");
        }
        slotRepository.deleteAll(slots);
        slotRepository.flush();
        return toDto(id);
    }

    @Override
    @Transactional
    public CharacterInventoryDto renameSlotGroup(String characterId, String groupName, String newName) {
        UUID id = access.loadOwned(characterId).getId();
        String from = requireGroup(groupName);
        String to = requireGroup(newName);
        List<CharacterInventorySlot> slots = slotRepository.findByCharacterIdAndGroupName(id, from);
        if (slots.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No slots in the group " + from);
        }
        if (!from.equals(to)) {
            if (!slotRepository.findByCharacterIdAndGroupName(id, to).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "There is already a group called " + to);
            }
            for (CharacterInventorySlot slot : slots) {
                slot.setGroupName(to);
                slot.setLabel(renamedLabel(slot.getLabel(), from, to));
            }
            slotRepository.saveAllAndFlush(slots);
        }
        return toDto(id);
    }

    // ----------------------------
    // Attunement slots
    // ----------------------------

    @Override
    @Transactional
    public CharacterInventoryDto addAttunementSlots(String characterId, SaveInventorySlotRequest r) {
        UUID id = access.loadOwned(characterId).getId();
        String group = blankToNull(r.group_name());
        List<String> existing = group == null ? List.of()
                : attunementSlotRepository.findByCharacterIdAndGroupName(id, group).stream()
                        .map(CharacterAttunementSlot::getLabel).toList();
        int order = (int) attunementSlotRepository.countByCharacterId(id);
        for (String label : newLabels(r, group, existing)) {
            CharacterAttunementSlot slot = new CharacterAttunementSlot();
            slot.setCharacterId(id);
            slot.setLabel(label);
            slot.setGroupName(group);
            slot.setNotes(r.notes() == null ? "" : r.notes());
            slot.setSortOrder(r.sort_order() == null ? order++ : r.sort_order());
            attunementSlotRepository.save(slot);
        }
        attunementSlotRepository.flush();
        return toDto(id);
    }

    @Override
    @Transactional
    public CharacterInventoryDto updateAttunementSlot(String characterId, String slotId, SaveInventorySlotRequest r) {
        UUID id = access.loadOwned(characterId).getId();
        CharacterAttunementSlot slot = attunementSlot(id, slotId);
        if (r.label() != null) slot.setLabel(r.label().trim());
        if (r.notes() != null) slot.setNotes(r.notes());
        if (r.sort_order() != null) slot.setSortOrder(r.sort_order());
        attunementSlotRepository.saveAndFlush(slot);
        return toDto(id);
    }

    @Override
    @Transactional
    public CharacterInventoryDto deleteAttunementSlot(String characterId, String slotId) {
        UUID id = access.loadOwned(characterId).getId();
        attunementSlotRepository.delete(attunementSlot(id, slotId));
        attunementSlotRepository.flush();
        requireAttunementsWithinLimit(id, "Removing it");
        return toDto(id);
    }

    @Override
    @Transactional
    public CharacterInventoryDto deleteAttunementSlotGroup(String characterId, String groupName) {
        UUID id = access.loadOwned(characterId).getId();
        List<CharacterAttunementSlot> slots = attunementSlotRepository.findByCharacterIdAndGroupName(id, requireGroup(groupName));
        if (slots.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No attunement slots in the group " + groupName.trim());
        }
        attunementSlotRepository.deleteAll(slots);
        attunementSlotRepository.flush();
        requireAttunementsWithinLimit(id, "Removing them");
        return toDto(id);
    }

    @Override
    @Transactional
    public CharacterInventoryDto renameAttunementSlotGroup(String characterId, String groupName, String newName) {
        UUID id = access.loadOwned(characterId).getId();
        String from = requireGroup(groupName);
        String to = requireGroup(newName);
        List<CharacterAttunementSlot> slots = attunementSlotRepository.findByCharacterIdAndGroupName(id, from);
        if (slots.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No attunement slots in the group " + from);
        }
        if (!from.equals(to)) {
            if (!attunementSlotRepository.findByCharacterIdAndGroupName(id, to).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "There is already a group called " + to);
            }
            for (CharacterAttunementSlot slot : slots) {
                slot.setGroupName(to);
                slot.setLabel(renamedLabel(slot.getLabel(), from, to));
            }
            attunementSlotRepository.saveAllAndFlush(slots);
        }
        return toDto(id);
    }

    /** Refuses (409, rolling back) a change that leaves more attuned items than the effective limit. */
    private void requireAttunementsWithinLimit(UUID characterId, String change) {
        long attuned = itemRepository.countByCharacterIdAndAttunedTrue(characterId);
        int limit = attunementLimit(characterId);
        if (attuned > limit) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, change + " would leave " + attuned
                    + " attuned items with a limit of " + limit + "; end an attunement first");
        }
    }

    /** Base limit plus one per attunement slot. */
    private int attunementLimit(UUID characterId) {
        return inventory(characterId).getAttunementLimit() + (int) attunementSlotRepository.countByCharacterId(characterId);
    }

    // ----------------------------
    // Items
    // ----------------------------

    @Override
    @Transactional
    public CharacterInventoryDto addItem(String characterId, SaveCharacterItemRequest r) {
        UUID id = access.loadOwned(characterId).getId();
        // A slot_id says where the item goes; otherwise the location must be given.
        if (r.location() == null && (r.slot_id() == null || r.slot_id().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "location is required (or a slot_id)");
        }
        CharacterItem item = new CharacterItem();
        item.setCharacterId(id);
        Item compendium = null;
        if (r.item_id() != null && !r.item_id().isBlank()) {
            compendium = compendiumItems.findById(CharacterAccess.parseUuid(r.item_id(), "item"))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found"));
            item.setItemId(compendium.getId());
            item.setChargesRemaining(compendium.getCharges());
        }
        applyItem(id, item, r, compendium);
        return toDto(id);
    }

    @Override
    @Transactional
    public CharacterInventoryDto updateItem(String characterId, String itemId, SaveCharacterItemRequest r) {
        UUID id = access.loadOwned(characterId).getId();
        CharacterItem item = itemRepository.findByIdAndCharacterId(CharacterAccess.parseUuid(itemId, "item"), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        if (r.item_id() != null && !r.item_id().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An item's compendium item cannot change; remove it and add the other item");
        }
        Item compendium = item.getItemId() == null ? null : compendiumItems.findById(item.getItemId()).orElse(null);
        applyItem(id, item, r, compendium);
        return toDto(id);
    }

    @Override
    @Transactional
    public CharacterInventoryDto deleteItem(String characterId, String itemId) {
        UUID id = access.loadOwned(characterId).getId();
        CharacterItem item = itemRepository.findByIdAndCharacterId(CharacterAccess.parseUuid(itemId, "item"), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        itemRepository.delete(item);
        itemRepository.flush();
        return toDto(id);
    }

    /** Applies the set fields to an item, checks the inventory rules, and saves it. */
    private void applyItem(UUID characterId, CharacterItem item, SaveCharacterItemRequest r, Item compendium) {
        if (r.custom_name() != null) item.setCustomName(r.custom_name().isBlank() ? null : r.custom_name().trim());
        if (item.getItemId() == null && item.getCustomName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give a compendium item_id or a custom_name");
        }
        if (r.location() != null) {
            item.setLocation(r.location());
            item.setSlotId(null);
        }
        if (r.slot_id() != null && !r.slot_id().isBlank()) {
            CharacterInventorySlot slot = slot(characterId, r.slot_id());
            item.setLocation(InventoryLocation.SLOT);
            item.setSlotId(slot.getId());
        }
        if (item.getLocation() == InventoryLocation.SLOT && item.getSlotId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a slot (slot_id) for location SLOT");
        }
        if (r.quantity() != null) item.setQuantity(r.quantity());
        if (Boolean.TRUE.equals(r.reset_stack_limit())) item.setStackLimit(null);
        if (r.stack_limit() != null) item.setStackLimit(r.stack_limit());
        if (r.charges_remaining() != null) item.setChargesRemaining(r.charges_remaining());
        if (r.notes() != null) item.setNotes(r.notes());
        if (r.sort_order() != null) item.setSortOrder(r.sort_order());
        if (r.attuned() != null) item.setAttuned(r.attuned());

        Integer limit = effectiveStackLimit(item, compendium, inventory(characterId).getStackLimitMultiplier());
        if (item.getLocation() == InventoryLocation.SLOT && limit != null && item.getQuantity() > limit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A slot holds at most " + limit + " "
                    + displayName(item, compendium) + "; split the rest into another slot");
        }
        if (Boolean.TRUE.equals(r.attuned()) && compendium != null && !compendium.isRequiresAttunement()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, compendium.getName() + " does not need attunement");
        }
        try {
            itemRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflictMessage(ex), ex);
        }
        if (item.isAttuned()) {
            long attuned = itemRepository.countByCharacterIdAndAttunedTrue(characterId);
            int max = attunementLimit(characterId);
            if (attuned > max) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The character can attune to " + max
                        + " items at a time; end an attunement first (or add an attunement slot)");
            }
        }
    }

    /** The stack's own limit, else the item's stack limit x the character's multiplier (null for custom items). */
    private static Integer effectiveStackLimit(CharacterItem item, Item compendium, int multiplier) {
        if (item.getStackLimit() != null) return item.getStackLimit();
        if (compendium == null || compendium.getStackLimit() == null) return null;
        return compendium.getStackLimit() * multiplier;
    }

    /** A readable message for an inventory rule the database refused. */
    private static String conflictMessage(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (message.contains("idx_character_items_one_armor")) return "The character is already wearing armor";
        if (message.contains("idx_character_items_one_bag")) return "The character already has a bag equipped";
        if (message.contains("idx_character_items_left_hand")) return "The left hand is already holding something";
        if (message.contains("idx_character_items_right_hand")) return "The right hand is already holding something";
        if (message.contains("idx_character_items_one_per_slot")) return "That slot already holds a stack";
        return "That change conflicts with the rest of the inventory";
    }

    // ----------------------------
    // Groups
    // ----------------------------

    /**
     * The labels for new slots. With a group: "<group> <n>", numbered on from the highest number the group already
     * uses (so adding 2 to "Spatial Storage 1..3" makes 4 and 5). Without a group: the label as given (one slot).
     */
    static List<String> newLabels(SaveInventorySlotRequest r, String group, List<String> existingLabels) {
        int count = r.count() == null ? 1 : r.count();
        if (group == null) {
            if (count > 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Name the group (group_name) to add several slots; they are labelled \"<group> 1\", \"<group> 2\", ...");
            }
            return List.of(r.label() == null ? "" : r.label().trim());
        }
        Pattern numbered = Pattern.compile("^" + Pattern.quote(group) + " (\\d+)$");
        int highest = 0;
        for (String label : existingLabels) {
            Matcher m = numbered.matcher(label);
            if (m.matches()) highest = Math.max(highest, Integer.parseInt(m.group(1)));
        }
        List<String> labels = new ArrayList<>();
        for (int n = highest + 1; n <= highest + count; n++) {
            labels.add(group + " " + n);
        }
        return labels;
    }

    /** "Old 3" becomes "New 3"; a label the player changed by hand stays as it is. */
    static String renamedLabel(String label, String from, String to) {
        Matcher m = Pattern.compile("^" + Pattern.quote(from) + " (\\d+)$").matcher(label);
        return m.matches() ? to + " " + m.group(1) : label;
    }

    private static String requireGroup(String groupName) {
        String group = blankToNull(groupName);
        if (group == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "group is required");
        }
        return group;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ----------------------------
    // Reading
    // ----------------------------

    private CharacterInventory inventory(UUID characterId) {
        return inventoryRepository.findById(characterId).orElseGet(() -> {
            CharacterInventory created = new CharacterInventory();
            created.setCharacterId(characterId);
            return inventoryRepository.saveAndFlush(created);
        });
    }

    private CharacterInventorySlot slot(UUID characterId, String slotId) {
        return slotRepository.findByIdAndCharacterId(CharacterAccess.parseUuid(slotId, "slot"), characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
    }

    private CharacterAttunementSlot attunementSlot(UUID characterId, String slotId) {
        return attunementSlotRepository.findByIdAndCharacterId(CharacterAccess.parseUuid(slotId, "attunement slot"), characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attunement slot not found"));
    }

    private CharacterInventoryDto toDto(UUID characterId) {
        CharacterInventory inventory = inventory(characterId);
        int multiplier = inventory.getStackLimitMultiplier();
        List<CharacterItem> items = itemRepository.findByCharacterIdOrderBySortOrderAscCreatedAtAsc(characterId);
        Map<UUID, Item> compendium = compendiumItems.findAllById(items.stream().map(CharacterItem::getItemId)
                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<UUID, CharacterItemDto> bySlot = new HashMap<>();
        CharacterItemDto armor = null, leftHand = null, rightHand = null, bothHands = null, bag = null;
        List<CharacterItemDto> components = new ArrayList<>();
        List<CharacterItemDto> quest = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int attuned = 0;

        for (CharacterItem item : items) {
            Item c = item.getItemId() == null ? null : compendium.get(item.getItemId());
            CharacterItemDto dto = toItemDto(item, c, multiplier);
            if (item.isAttuned()) attuned++;
            if (item.getLocation() == InventoryLocation.SLOT && dto.stackLimit() != null && item.getQuantity() > dto.stackLimit()) {
                warnings.add(dto.name() + ": " + item.getQuantity() + " in one slot, but it stacks to " + dto.stackLimit());
            }
            switch (item.getLocation()) {
                case ARMOR -> armor = dto;
                case LEFT_HAND -> leftHand = dto;
                case RIGHT_HAND -> rightHand = dto;
                case BOTH_HANDS -> bothHands = dto;
                case BAG -> bag = dto;
                case COMPONENTS -> components.add(dto);
                case QUEST -> quest.add(dto);
                case SLOT -> bySlot.put(item.getSlotId(), dto);
            }
        }
        List<AttunementSlotDto> attunementSlots = attunementSlotRepository
                .findByCharacterIdOrderBySortOrderAscCreatedAtAsc(characterId).stream()
                .map(s -> new AttunementSlotDto(s.getId().toString(), s.getLabel(), s.getGroupName(), s.getNotes(),
                        s.getSortOrder()))
                .toList();
        int attunementLimit = inventory.getAttunementLimit() + attunementSlots.size();
        if (attuned > attunementLimit) {
            warnings.add("Attuned to " + attuned + " items; the limit is " + attunementLimit);
        }
        List<InventorySlotDto> slots = slotRepository.findByCharacterIdOrderBySortOrderAscCreatedAtAsc(characterId).stream()
                .map(s -> new InventorySlotDto(s.getId().toString(), s.getLabel(), s.getGroupName(), s.getNotes(),
                        s.getSortOrder(), bySlot.get(s.getId())))
                .toList();
        long wealth = inventory.getPlatinum() * 10_000L + inventory.getGold() * 100L + inventory.getSilver() * 10L
                + inventory.getBronze();
        return new CharacterInventoryDto(inventory.getPlatinum(), inventory.getGold(), inventory.getSilver(),
                inventory.getBronze(), wealth, inventory.getAttunementLimit(), attunementLimit, attuned, multiplier,
                inventory.getNotes(), armor, leftHand, rightHand, bothHands, bag, components, quest, slots,
                attunementSlots, warnings);
    }

    private static String displayName(CharacterItem item, Item c) {
        return item.getCustomName() != null ? item.getCustomName() : c == null ? "items" : c.getName();
    }

    private static CharacterItemDto toItemDto(CharacterItem item, Item c, int multiplier) {
        String compendiumName = c == null ? null : c.getName();
        return new CharacterItemDto(item.getId().toString(),
                item.getItemId() == null ? null : item.getItemId().toString(),
                displayName(item, c), item.getCustomName(), compendiumName,
                c == null || c.getItemType() == null ? null : c.getItemType().name(),
                item.getLocation(), item.getSlotId() == null ? null : item.getSlotId().toString(),
                item.getQuantity(), effectiveStackLimit(item, c, multiplier), c == null ? null : c.getStackLimit(),
                item.getStackLimit(), item.isAttuned(), c != null && c.isRequiresAttunement(),
                item.getChargesRemaining(), c == null ? null : c.getCharges(), item.getNotes(), item.getSortOrder());
    }
}
