package com.journey.api_rest_v1.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.journey.api_rest_v1.domain.creatures.AugmentKind;
import com.journey.api_rest_v1.domain.creatures.Creature;
import com.journey.api_rest_v1.domain.creatures.CreatureAbility;
import com.journey.api_rest_v1.domain.creatures.CreatureAttack;
import com.journey.api_rest_v1.domain.creatures.CreatureAugment;
import com.journey.api_rest_v1.domain.creatures.CreatureType;
import com.journey.api_rest_v1.domain.items.DamageType;
import com.journey.api_rest_v1.domain.items.StatusCondition;
import com.journey.api_rest_v1.domain.items.WeaponProperty;
import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.races.RaceSize;
import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.dto.creature.CreateCreatureRequest;
import com.journey.api_rest_v1.dto.creature.CreatureFilterRequest;
import com.journey.api_rest_v1.dto.creature.CreatureResponseDto;
import com.journey.api_rest_v1.dto.creature.CreatureTypeDto;
import com.journey.api_rest_v1.mapper.CreatureMapper;
import com.journey.api_rest_v1.repository.ContentSourceRepository;
import com.journey.api_rest_v1.repository.CreatureRepository;
import com.journey.api_rest_v1.repository.CreatureTypeRepository;
import com.journey.api_rest_v1.repository.DamageTypeRepository;
import com.journey.api_rest_v1.repository.PowerRepository;
import com.journey.api_rest_v1.repository.StatusConditionRepository;
import com.journey.api_rest_v1.repository.TraitRepository;
import com.journey.api_rest_v1.repository.WeaponPropertyRepository;
import com.journey.api_rest_v1.service.CreatureService;
import com.journey.api_rest_v1.service.campaign.ContentAccess;
import com.journey.api_rest_v1.specification.CreatureSpecifications;
import com.journey.api_rest_v1.specification.SourceSpecifications;

/**
 * Default {@link CreatureService} implementation backed by JPA, with Redis caching for reads.
 * Follows the same structure as {@code RaceServiceImpl}.
 *
 * <p>Rules: the creature type must belong to the creature's content source, a size range must not be
 * reversed, an ability links to at most one of a trait or power, every augment targets a known damage type
 * or status condition, and attack damage types/properties must exist.</p>
 */
@Service
public class CreatureServiceImpl implements CreatureService {

    /** Who may read campaign content (set by Spring). */
    private ContentAccess contentAccess;

    @org.springframework.beans.factory.annotation.Autowired
    void setContentAccess(ContentAccess contentAccess) {
        this.contentAccess = contentAccess;
    }

    private static final String DEFAULT_SORT_FIELD = "name";

    private final CreatureRepository creatureRepository;
    private final CreatureTypeRepository creatureTypeRepository;
    private final ContentSourceRepository contentSourceRepository;
    private final DamageTypeRepository damageTypeRepository;
    private final WeaponPropertyRepository weaponPropertyRepository;
    private final TraitRepository traitRepository;
    private final PowerRepository powerRepository;
    private final StatusConditionRepository statusConditionRepository;
    private final CreatureMapper creatureMapper;

    /** Wires the repositories and mapper this service depends on. */
    public CreatureServiceImpl(CreatureRepository creatureRepository,
                               CreatureTypeRepository creatureTypeRepository,
                               ContentSourceRepository contentSourceRepository,
                               DamageTypeRepository damageTypeRepository,
                               WeaponPropertyRepository weaponPropertyRepository,
                               TraitRepository traitRepository,
                               PowerRepository powerRepository,
                               StatusConditionRepository statusConditionRepository,
                               CreatureMapper creatureMapper) {
        this.creatureRepository = creatureRepository;
        this.creatureTypeRepository = creatureTypeRepository;
        this.contentSourceRepository = contentSourceRepository;
        this.damageTypeRepository = damageTypeRepository;
        this.weaponPropertyRepository = weaponPropertyRepository;
        this.traitRepository = traitRepository;
        this.powerRepository = powerRepository;
        this.statusConditionRepository = statusConditionRepository;
        this.creatureMapper = creatureMapper;
    }

    /** Creates a creature, rejecting duplicate names with 409 and invalid references with 400/404. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"creaturesById", "creaturesAll"}, allEntries = true)
    public CreatureResponseDto createCreature(CreateCreatureRequest request) {
        if (creatureRepository.existsByNameKey(normalizeName(request.name()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A creature named '" + request.name().trim() + "' already exists");
        }

        Creature creature = new Creature();
        applyFields(creature, request);
        Creature saved = creatureRepository.save(creature);

        // The saved creature has its id now, which the augment keys need.
        addChildren(saved, request);

        return creatureMapper.CreateResponse(creatureRepository.save(saved));
    }

    /**
     * Replaces every field of a creature. Child rows are cleared and flushed before re-adding, because the
     * augment key (creature, kind, target) would otherwise collide mid-flush when an augment is kept.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"creaturesById", "creaturesAll"}, allEntries = true)
    public CreatureResponseDto updateCreature(String id, CreateCreatureRequest request) {
        UUID creatureId = parseUuid(id, "creature");
        Creature existing = findCreature(creatureId);

        if (creatureRepository.existsByNameKeyAndIdNot(normalizeName(request.name()), creatureId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A creature named '" + request.name().trim() + "' already exists");
        }

        applyFields(existing, request);
        existing.getAugments().clear();
        existing.getAbilities().clear();
        existing.getAttacks().clear();
        creatureRepository.saveAndFlush(existing);

        addChildren(existing, request);

        return creatureMapper.CreateResponse(creatureRepository.save(existing));
    }

    /** Deletes a creature; its augments, abilities, and attacks are removed with it. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"creaturesById", "creaturesAll"}, allEntries = true)
    public String deleteCreature(String id) {
        Creature existing = findCreature(parseUuid(id, "creature"));
        String name = existing.getName();
        creatureRepository.delete(existing);
        return "Creature with id " + id + " (name: " + name + ") has been deleted.";
    }

    /** Returns one creature by id, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "creaturesById", key = "#id")
    public CreatureResponseDto getCreatureById(String id) {
        return creatureMapper.CreateResponse(findCreature(parseUuid(id, "creature")));
    }

    /** Returns a sorted page of all creatures, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "creaturesAll", key = "#page + ':' + #size + ':' + #sortBy + ':' + #sortDir")
    public Page<CreatureResponseDto> getAllCreatures(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        // Official content only, so this cached list is the same for everyone (campaign content: use the filter).
        return creatureRepository.findAll(SourceSpecifications.<Creature>notInSources(contentAccess.campaignSources()), pageable).map(creatureMapper::CreateResponse);
    }

    /** Returns creature types for one source, or every source when contentSourceId is null/blank. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "creatureTypes", key = "#contentSourceId == null ? 'all' : #contentSourceId")
    public List<CreatureTypeDto> getCreatureTypes(String contentSourceId) {
        Sort byName = Sort.by(Sort.Direction.ASC, "name");
        // Without a source: the official types only (each campaign has its own copies of them).
        List<UUID> campaigns = contentAccess.campaignSources();
        List<CreatureType> types = contentSourceId == null || contentSourceId.isBlank()
                ? creatureTypeRepository.findAll(byName).stream().filter(t -> !campaigns.contains(t.getSource().getId())).toList()
                : creatureTypeRepository.findAllBySource_Id(parseUuid(contentSourceId, "content source"), byName);
        return types.stream().map(creatureMapper::CreateTypeResponse).toList();
    }

    /**
     * Applies every filter on the request. When a name is given, an exact match is returned alone;
     * otherwise results are ranked so names containing the search come first, then by edit distance.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<CreatureResponseDto> filterCreatures(CreatureFilterRequest request) {
        Specification<Creature> specification = CreatureSpecifications.all()
                .and(SourceSpecifications.<Creature>inSources(request.sourceIds()))
                .and(SourceSpecifications.<Creature>notInSources(contentAccess.hiddenSources(ContentAccess.Kind.CREATURE)))
                .and(CreatureSpecifications.withTypes(request.creatureTypes()))
                .and(CreatureSpecifications.withThreatLvl(request.minThreatLvl(), request.maxThreatLvl()))
                .and(CreatureSpecifications.withSizes(request.sizes()))
                .and(CreatureSpecifications.withAugment(AugmentKind.WEAK, request.weakTo()))
                .and(CreatureSpecifications.withAugment(AugmentKind.RESISTANT, request.resistantTo()))
                .and(CreatureSpecifications.withAugment(AugmentKind.IMMUNE, request.immuneTo()));

        boolean searchByName = request.name() != null && !request.name().isBlank();
        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                searchByName ? Sort.unsorted() : Sort.by(Sort.Direction.ASC, DEFAULT_SORT_FIELD));

        if (!searchByName) {
            return creatureRepository.findAll(specification, pageable).map(creatureMapper::CreateResponse);
        }

        String searchName = normalizeName(request.name());
        List<Creature> exactMatches = creatureRepository
                .findAll(specification.and(CreatureSpecifications.withExactName(searchName)), PageRequest.of(0, 1))
                .getContent();
        if (!exactMatches.isEmpty()) {
            CreatureResponseDto exactMatch = creatureMapper.CreateResponse(exactMatches.get(0));
            return new PageImpl<>(List.of(exactMatch), PageRequest.of(0, request.getSize()), 1);
        }

        List<Creature> ranked = creatureRepository.findAll(specification).stream()
                .sorted(Comparator
                        .comparingInt((Creature creature) ->
                                creature.getName().toLowerCase(Locale.ROOT).contains(searchName) ? 0 : 1)
                        .thenComparingInt(creature -> levenshteinDistance(
                                searchName, creature.getName().toLowerCase(Locale.ROOT)))
                        .thenComparing(Creature::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int start = (int) Math.min(pageable.getOffset(), ranked.size());
        int end = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), ranked.size());
        List<CreatureResponseDto> pageContent = ranked.subList(start, end).stream()
                .map(creatureMapper::CreateResponse)
                .toList();
        return new PageImpl<>(pageContent, pageable, ranked.size());
    }

    /** Copies the request's scalar fields onto the creature, validating the type and size range. */
    private void applyFields(Creature creature, CreateCreatureRequest request) {
        ContentSource source = findContentSource(request.content_source_id());

        CreatureType type = creatureTypeRepository
                .findBySource_IdAndNameKey(source.getId(), normalizeName(request.creature_type()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown creature type '" + request.creature_type() + "' for source '"
                                + source.getSource_name() + "'. Valid: "
                                + creatureTypeRepository.findAllBySource_Id(source.getId(), Sort.by("name"))
                                        .stream().map(CreatureType::getName).toList()));

        creature.setName(request.name().trim());
        creature.setSource(source);
        creature.setCreatureType(type);
        creature.setThreatLvl(request.threat_lvl());
        creature.setMinSize(request.min_size());
        creature.setMaxSize(validMaxSize(request.min_size(), request.max_size()));
        creature.setDescription(blankToNull(request.description()));
        creature.setLanguages(blankToNull(request.languages()));
        creature.setLore(blankToNull(request.lore()));
        creature.setHp(request.hp());
        creature.setWp(request.wp());
        creature.setMp(orZero(request.mp()));
        creature.setEp(orZero(request.ep()));
        creature.setPrio(orZero(request.prio()));
        creature.setMovement(request.movement() == null ? 30 : request.movement());
        creature.setPd(orZero(request.pd()));
        creature.setMd(orZero(request.md()));

        List<Map<String, Object>> skills = new ArrayList<>();
        if (request.skills() != null) {
            for (CreateCreatureRequest.Skill skill : request.skills()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("skill", skill.skill().trim());
                entry.put("rating", skill.rating());
                skills.add(entry);
            }
        }
        creature.setSkills(skills);
    }

    /** Adds augments, abilities, and attacks (in request order) to a managed creature. */
    private void addChildren(Creature creature, CreateCreatureRequest request) {
        Set<String> seenAugments = new LinkedHashSet<>();
        for (CreateCreatureRequest.Augment augment : nullSafe(request.augments())) {
            String targetKey = normalizeName(augment.target());
            DamageType damageType = damageTypeRepository.findByNameKey(targetKey).orElse(null);
            StatusCondition condition = damageType != null ? null
                    : statusConditionRepository.findByNameKey(targetKey).orElse(null);
            if (damageType == null && condition == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Augment target '" + augment.target() + "' is not a damage type or status condition");
            }
            if (seenAugments.add(augment.kind() + ":" + targetKey)) {
                creature.getAugments().add(damageType != null
                        ? new CreatureAugment(creature, augment.kind(), damageType)
                        : new CreatureAugment(creature, augment.kind(), condition));
            }
        }

        int order = 0;
        for (CreateCreatureRequest.Ability ability : nullSafe(request.abilities())) {
            boolean hasTrait = ability.trait_id() != null && !ability.trait_id().isBlank();
            boolean hasPower = ability.power_id() != null && !ability.power_id().isBlank();
            if (hasTrait && hasPower) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ability '" + ability.name() + "' can link to a trait or a power, not both");
            }
            Trait trait = hasTrait ? findTrait(ability.trait_id()) : null;
            Power power = hasPower ? findPower(ability.power_id()) : null;

            creature.getAbilities().add(new CreatureAbility(
                    creature, ability.timing(), ability.name().trim(), ability.effect().trim(), trait, power,
                    ability.lvl(), ability.uses(), ability.mp_cost(), ability.ep_cost(), ability.tp_cost(), order++));
        }

        order = 0;
        for (CreateCreatureRequest.Attack attack : nullSafe(request.attacks())) {
            DamageType damageType = damageTypeRepository.findByNameKey(normalizeName(attack.damage_type()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Attack '" + attack.name() + "': unknown damage type '" + attack.damage_type() + "'. Valid: "
                                    + damageTypeRepository.findAll(Sort.by("name")).stream().map(DamageType::getName).toList()));

            Set<WeaponProperty> properties = new LinkedHashSet<>();
            for (String name : nullSafe(attack.properties())) {
                properties.add(weaponPropertyRepository.findByNameKey(normalizeName(name))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Attack '" + attack.name() + "': unknown weapon property '" + name + "'. Valid: "
                                        + weaponPropertyRepository.findAll(Sort.by("name")).stream()
                                                .map(WeaponProperty::getName).toList())));
            }

            creature.getAttacks().add(new CreatureAttack(
                    creature, attack.name().trim(), attack.skill().trim(), attack.range_ft(),
                    attack.damage_dice().trim().toLowerCase(Locale.ROOT), blankToNull(attack.damage_bonus()),
                    damageType, attack.mag(), order++, properties));
        }
    }

    /** Null when absent or equal to the minimum (a single size); 400 when smaller than the minimum. */
    private static RaceSize validMaxSize(RaceSize minSize, RaceSize maxSize) {
        if (maxSize == null || maxSize == minSize) {
            return null;
        }
        if (maxSize.ordinal() < minSize.ordinal()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Max size " + maxSize + " must not be smaller than min size " + minSize);
        }
        return maxSize;
    }

    private Trait findTrait(String id) {
        return traitRepository.findById(parseUuid(id, "trait"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trait not found with id: " + id));
    }

    private Power findPower(String id) {
        return powerRepository.findById(parseUuid(id, "power"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Power not found with id: " + id));
    }

    private Creature findCreature(UUID creatureId) {
        return creatureRepository.findById(creatureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Creature not found with id: " + creatureId));
    }

    private ContentSource findContentSource(String contentSourceId) {
        UUID sourceId = parseUuid(contentSourceId, "content source");
        return contentSourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Content source not found with id: " + contentSourceId));
    }

    private static UUID parseUuid(String value, String label) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + label + " id: " + value);
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Integer orZero(Integer value) {
        return value == null ? 0 : value;
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
