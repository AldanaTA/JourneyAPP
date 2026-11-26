package com.journey.api_rest_v1.service.impl;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.journey.api_rest_v1.domain.content_source.ContentSource;
import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.powers.PowerCategories;
import com.journey.api_rest_v1.domain.powers.PowerCategoriesId;
import com.journey.api_rest_v1.domain.powers.PowerCategory;
import com.journey.api_rest_v1.dto.power.CreatePowerRequest;
import com.journey.api_rest_v1.dto.power.PowerFilterRequest;
import com.journey.api_rest_v1.dto.power.PowerResponseDto;
import com.journey.api_rest_v1.mapper.PowerMapper;
import com.journey.api_rest_v1.repository.ContentSourceRepository;
import com.journey.api_rest_v1.repository.PowerCategoriesRepository;
import com.journey.api_rest_v1.repository.PowerRepository;
import com.journey.api_rest_v1.service.PowerService;
import com.journey.api_rest_v1.specification.SourceSpecifications;
import com.journey.api_rest_v1.specification.PowerSpecifications;

/**
 * Default {@link PowerService} implementation backed by JPA, with Redis caching for reads.
 */
@Service
public class PowerServiceImpl implements PowerService {

    private final PowerRepository powerRepository;
    private final PowerCategoriesRepository powerCategoriesRepository;
    private final ContentSourceRepository contentSourceRepository;
    private final PowerMapper powerMapper;

    /** Wires the repositories and mapper this service depends on. */
    public PowerServiceImpl(PowerRepository powerRepository,
                           PowerCategoriesRepository powerCategoriesRepository,
                           ContentSourceRepository contentSourceRepository,
                           PowerMapper powerMapper) {
        this.powerRepository = powerRepository;
        this.powerCategoriesRepository = powerCategoriesRepository;
        this.contentSourceRepository = contentSourceRepository;
        this.powerMapper = powerMapper;
    }

    /** Creates a power, rejecting duplicate names with 409, then attaches its (de-duplicated) categories. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"powersById", "powersAll", "creaturesById", "creaturesAll"}, allEntries = true)
    public PowerResponseDto createPower(CreatePowerRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        ContentSource contentSource = findContentSource(request.content_source_id());

        if (powerRepository.existsByNameKey(normalizeName(request.name()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A power named '" + request.name().trim() + "' already exists");
        }

        Power power = new Power(
                null,
                request.name(),
                request.lvl(),
                request.type(),
                request.tp_cost(),
                request.hp_cost(),
                request.mp_cost(),
                request.ep_cost(),
                request.material_components(),
                request.verbal(),
                request.sight(),
                request.somatic(),
                request.is_distinct(),
                request.concentration(),
                request.range(),
                request.area(),
                request.duration(),
                request.effect(),
                request.empower_effect(),
                request.lvl_up_effect(),
                new java.util.ArrayList<>(),
                contentSource,
                now,
                now
        );

        Power savedPower = powerRepository.save(power);

        List<PowerCategories> powerCategories = distinctCategories(request.categories()).stream()
                        .map(category -> {
                            PowerCategories link = new PowerCategories(
                                    new PowerCategoriesId(savedPower.getId(), category),
                                    savedPower,
                                    category,
                                    now,
                                    now
                            );
                            savedPower.getCategories().add(link);
                            return link;
                        })
                        .toList();

        if (!powerCategories.isEmpty()) {
            powerCategoriesRepository.saveAll(powerCategories);
        }

        return powerMapper.CreateResponse(savedPower);
    }

    /** Updates every field of a power and syncs its categories by only adding/removing what changed. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"powersById", "powersAll", "creaturesById", "creaturesAll"}, allEntries = true)
    public PowerResponseDto updatePower(String id, CreatePowerRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID powerId = parseUuid(id, "power");
        Power existingPower = findPower(powerId);
        ContentSource contentSource = findContentSource(request.content_source_id());

        if (powerRepository.existsByNameKeyAndIdNot(normalizeName(request.name()), powerId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A power named '" + request.name().trim() + "' already exists");
        }

        existingPower.setName(request.name());
        existingPower.setLvl(request.lvl());
        existingPower.setType(request.type());
        existingPower.setTpCost(request.tp_cost());
        existingPower.setHpCost(request.hp_cost());
        existingPower.setMpCost(request.mp_cost());
        existingPower.setEpCost(request.ep_cost());
        existingPower.setMaterialComponents(request.material_components());
        existingPower.setVerbal(request.verbal());
        existingPower.setSight(request.sight());
        existingPower.setSomatic(request.somatic());
        existingPower.setIsDistinct(request.is_distinct());
        existingPower.setConcentration(request.concentration());
        existingPower.setRange(request.range());
        existingPower.setArea(request.area());
        existingPower.setDuration(request.duration());
        existingPower.setEffect(request.effect());
        existingPower.setEmpowerEffect(request.empower_effect());
        existingPower.setLvlUpEffect(request.lvl_up_effect());
        existingPower.setSource(contentSource);
        existingPower.setUpdatedAt(now);

        // Diff the categories instead of delete-all/re-insert, which can collide on the composite key in one flush.
        Set<PowerCategory> desired = distinctCategories(request.categories());
        existingPower.getCategories().removeIf(link -> !desired.contains(link.getCategory()));
        Set<PowerCategory> current = existingPower.getCategories().stream()
                .map(PowerCategories::getCategory)
                .collect(Collectors.toSet());
        desired.stream()
                .filter(category -> !current.contains(category))
                .forEach(category -> existingPower.getCategories().add(new PowerCategories(
                        new PowerCategoriesId(existingPower.getId(), category), existingPower, category, now, now)));

        return powerMapper.CreateResponse(powerRepository.save(existingPower));
    }

    /** Deletes a power; its category links are removed by cascade/orphan removal. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"powersById", "powersAll", "creaturesById", "creaturesAll"}, allEntries = true)
    public String deletePower(String id) {
        Power existingPower = findPower(parseUuid(id, "power"));
        String name = existingPower.getName();
        powerRepository.delete(existingPower);

        return "Power with id " + id + " (name: " + name + ") has been deleted.";
    }

    /** Returns one power by id (404 when missing), served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "powersById", key = "#id")
    public PowerResponseDto getPowerById(String id) {
        return powerMapper.CreateResponse(findPower(parseUuid(id, "power")));
    }

    /** Returns a sorted page of all powers, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "powersAll", key = "#page + ':' + #size + ':' + #sortBy + ':' + #sortDir")
    public Page<PowerResponseDto> getAllPowers(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        return powerRepository.findAll(pageable).map(powerMapper::CreateResponse);
    }

    /** Returns powers matching any/all of the given categories, sorted by name. */
    @Override
    @Transactional(readOnly = true)
    public Page<PowerResponseDto> getPowersByCategory(List<PowerCategory> categories, boolean matchAll, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        if (matchAll) {
            return powerRepository.findPowersWithAllCategories(categories, categories.size(), pageable)
                    .map(powerMapper::CreateResponse);
        } else {
            return powerRepository.findByCategories_CategoryIn(categories, pageable)
                    .map(powerMapper::CreateResponse);
        }
    }

    /**
     * Applies every filter on the request. When a name is given, an exact match is returned alone;
     * otherwise names containing the search come first, then the closest spellings (edit distance).
     */
    @Override
    @Transactional(readOnly = true)
    public Page<PowerResponseDto> filterPowers(PowerFilterRequest request) {
        Specification<Power> specification = PowerSpecifications.withType(request.type())
                .and(SourceSpecifications.<Power>inSources(request.sourceIds()));

        if (request.categories() != null && !request.categories().isEmpty()) {
            specification = specification.and(PowerSpecifications.withCategories(request.categories(), request.getMatchAll()));
        }

        if (request.tpCost() != null && request.tpCost() >= 0) {
            specification = specification.and(
                PowerSpecifications.withResourceFilter("tpCost", request.getTpOperator(), request.tpCost()));
        }

        if (request.hpCost() != null && request.hpCost() >= 0) {
            specification = specification.and(
                PowerSpecifications.withResourceFilter("hpCost", request.getHpOperator(), request.hpCost()));
        }

        if (request.mpCost() != null && request.mpCost() >= 0) {
            specification = specification.and(
                PowerSpecifications.withResourceFilter("mpCost", request.getMpOperator(), request.mpCost()));
        }

        if (request.epCost() != null && request.epCost() >= 0) {
            specification = specification.and(
                PowerSpecifications.withResourceFilter("epCost", request.getEpOperator(), request.epCost()));
        }

        boolean searchByName = request.name() != null && !request.name().isBlank();

        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                searchByName ? Sort.unsorted() : Sort.by(Sort.Direction.ASC, "name"));
        if (searchByName) {
            String searchName = request.name().trim().toLowerCase(Locale.ROOT);
            Specification<Power> exactNameSpecification = specification.and((root, query, cb) ->
                    cb.equal(cb.lower(root.<String>get("name")), searchName));
            List<Power> exactMatches = powerRepository.findAll(
                    exactNameSpecification,
                    PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "name")))
                    .getContent();
            if (!exactMatches.isEmpty()) {
                PowerResponseDto exactMatch = powerMapper.CreateResponse(exactMatches.get(0));
                Pageable exactMatchPage = PageRequest.of(0, request.getSize());
                return new PageImpl<>(List.of(exactMatch), exactMatchPage, 1);
            }

            List<Power> rankedPowers = powerRepository.findAll(specification).stream()
                    .sorted(Comparator
                            .comparingInt((Power power) ->
                                    power.getName().toLowerCase(Locale.ROOT).contains(searchName) ? 0 : 1)
                            .thenComparingInt(power -> levenshteinDistance(
                                    searchName, power.getName().toLowerCase(Locale.ROOT)))
                            .thenComparing(Power::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            int start = (int) Math.min(pageable.getOffset(), rankedPowers.size());
            int end = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), rankedPowers.size());
            List<PowerResponseDto> pageContent = rankedPowers.subList(start, end).stream()
                    .map(powerMapper::CreateResponse)
                    .toList();
            return new PageImpl<>(pageContent, pageable, rankedPowers.size());
        }

        return powerRepository.findAll(specification, pageable).map(powerMapper::CreateResponse);
    }

    /** Loads a power or throws 404 when it does not exist. */
    private Power findPower(UUID powerId) {
        return powerRepository.findById(powerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Power not found with id: " + powerId));
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

    /** Removes nulls and duplicates from the requested categories while keeping their order. */
    private static Set<PowerCategory> distinctCategories(List<PowerCategory> categories) {
        if (categories == null) {
            return new LinkedHashSet<>();
        }
        return categories.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Normalizes a name the same way the database computes {@code name_key}. */
    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
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
