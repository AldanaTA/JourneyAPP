package com.journey.api_rest_v1.service.impl;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.domain.traits.TraitCategories;
import com.journey.api_rest_v1.domain.traits.TraitCategoriesId;
import com.journey.api_rest_v1.domain.traits.TraitCategory;
import com.journey.api_rest_v1.dto.trait.CreateTraitRequest;
import com.journey.api_rest_v1.dto.trait.TraitFilterRequest;
import com.journey.api_rest_v1.dto.trait.TraitResponseDto;
import com.journey.api_rest_v1.mapper.TraitMapper;
import com.journey.api_rest_v1.repository.ContentSourceRepository;
import com.journey.api_rest_v1.repository.TraitRepository;
import com.journey.api_rest_v1.service.TraitService;
import com.journey.api_rest_v1.specification.TraitSpecifications;

/**
 * Default {@link TraitService} implementation backed by JPA, with Redis caching for reads.
 * Follows the same structure as {@code PowerServiceImpl}.
 */
@Service
public class TraitServiceImpl implements TraitService {

    private static final String DEFAULT_SORT_FIELD = "traitName";

    private final TraitRepository traitRepository;
    private final ContentSourceRepository contentSourceRepository;
    private final TraitMapper traitMapper;

    /** Wires the repositories and mapper this service depends on. */
    public TraitServiceImpl(TraitRepository traitRepository,
                            ContentSourceRepository contentSourceRepository,
                            TraitMapper traitMapper) {
        this.traitRepository = traitRepository;
        this.contentSourceRepository = contentSourceRepository;
        this.traitMapper = traitMapper;
    }

    /** Creates a trait, rejecting duplicate names with 409, then attaches its categories. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"traitsById", "traitsAll"}, allEntries = true)
    public TraitResponseDto createTrait(CreateTraitRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        ContentSource contentSource = findContentSource(request.content_source_id());

        if (traitRepository.existsByTraitNameKey(normalizeName(request.trait_name()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A trait named '" + request.trait_name().trim() + "' already exists");
        }

        Trait trait = new Trait(
                null,
                request.trait_name().trim(),
                request.lvl(),
                request.purchase_cost(),
                request.lvl_up_cost(),
                request.trait_desc(),
                orZero(request.trait_uses()),
                orZero(request.mp_cost()),
                orZero(request.ep_cost()),
                orZero(request.tp_cost()),
                request.trait_effect(),
                orNone(request.lvl_up_effect()),
                new java.util.ArrayList<>(),
                contentSource,
                now,
                now
        );

        Trait savedTrait = traitRepository.save(trait);

        // The saved trait is managed, so new links are persisted by cascade when the transaction flushes.
        for (TraitCategory category : distinctCategories(request.categories())) {
            savedTrait.getCategories().add(newLink(savedTrait, category, now));
        }

        return traitMapper.CreateResponse(savedTrait);
    }

    /** Updates every field of a trait and syncs its categories by only adding/removing what changed. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"traitsById", "traitsAll"}, allEntries = true)
    public TraitResponseDto updateTrait(String id, CreateTraitRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID traitId = parseUuid(id, "trait");
        Trait existingTrait = findTrait(traitId);
        ContentSource contentSource = findContentSource(request.content_source_id());

        if (traitRepository.existsByTraitNameKeyAndIdNot(normalizeName(request.trait_name()), traitId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A trait named '" + request.trait_name().trim() + "' already exists");
        }

        existingTrait.setTraitName(request.trait_name().trim());
        existingTrait.setLvl(request.lvl());
        existingTrait.setPurchaseCost(request.purchase_cost());
        existingTrait.setLvlUpCost(request.lvl_up_cost());
        existingTrait.setTraitDesc(request.trait_desc());
        existingTrait.setTraitUses(orZero(request.trait_uses()));
        existingTrait.setMpCost(orZero(request.mp_cost()));
        existingTrait.setEpCost(orZero(request.ep_cost()));
        existingTrait.setTpCost(orZero(request.tp_cost()));
        existingTrait.setTraitEffect(request.trait_effect());
        existingTrait.setLvlUpEffect(orNone(request.lvl_up_effect()));
        existingTrait.setSource(contentSource);
        existingTrait.setUpdatedAt(now);

        // Diff the categories instead of delete-all/re-insert, which can collide on the composite key in one flush.
        Set<TraitCategory> desired = distinctCategories(request.categories());
        existingTrait.getCategories().removeIf(link -> !desired.contains(link.getCategory()));
        Set<TraitCategory> current = existingTrait.getCategories().stream()
                .map(TraitCategories::getCategory)
                .collect(Collectors.toSet());
        desired.stream()
                .filter(category -> !current.contains(category))
                .forEach(category -> existingTrait.getCategories().add(newLink(existingTrait, category, now)));

        return traitMapper.CreateResponse(traitRepository.save(existingTrait));
    }

    /** Deletes a trait; its category links are removed by cascade/orphan removal. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"traitsById", "traitsAll"}, allEntries = true)
    public String deleteTrait(String id) {
        Trait existingTrait = findTrait(parseUuid(id, "trait"));
        String name = existingTrait.getTraitName();
        traitRepository.delete(existingTrait);
        return "Trait with id " + id + " (name: " + name + ") has been deleted.";
    }

    /** Returns one trait by id, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "traitsById", key = "#id")
    public TraitResponseDto getTraitById(String id) {
        return traitMapper.CreateResponse(findTrait(parseUuid(id, "trait")));
    }

    /** Returns a sorted page of all traits, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "traitsAll", key = "#page + ':' + #size + ':' + #sortBy + ':' + #sortDir")
    public Page<TraitResponseDto> getAllTraits(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        return traitRepository.findAll(pageable).map(traitMapper::CreateResponse);
    }

    /** Returns traits matching any/all of the given categories, sorted by name. */
    @Override
    @Transactional(readOnly = true)
    public Page<TraitResponseDto> getTraitsByCategory(List<TraitCategory> categories, boolean matchAll, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, DEFAULT_SORT_FIELD));
        if (categories == null || categories.isEmpty()) {
            return traitRepository.findAll(pageable).map(traitMapper::CreateResponse);
        }
        Set<TraitCategory> distinct = distinctCategories(categories);
        Page<Trait> traits = matchAll
                ? traitRepository.findTraitsWithAllCategories(distinct, distinct.size(), pageable)
                : traitRepository.findTraitsWithAnyCategory(distinct, pageable);
        return traits.map(traitMapper::CreateResponse);
    }

    /**
     * Applies every filter on the request. When a name is given, an exact match is returned alone;
     * otherwise results are ranked so names containing the search come first, then by edit distance.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TraitResponseDto> filterTraits(TraitFilterRequest request) {
        Specification<Trait> specification = TraitSpecifications.all();

        if (request.categories() != null && !request.categories().isEmpty()) {
            specification = specification.and(
                    TraitSpecifications.withCategories(request.categories(), request.getMatchAll()));
        }
        specification = specification
                .and(numericFilter("lvl", request.getLvlOperator(), request.lvl()))
                .and(numericFilter("purchaseCost", request.getPurchaseOperator(), request.purchaseCost()))
                .and(numericFilter("tpCost", request.getTpOperator(), request.tpCost()))
                .and(numericFilter("mpCost", request.getMpOperator(), request.mpCost()))
                .and(numericFilter("epCost", request.getEpOperator(), request.epCost()));

        boolean searchByName = request.name() != null && !request.name().isBlank();
        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                searchByName ? Sort.unsorted() : Sort.by(Sort.Direction.ASC, DEFAULT_SORT_FIELD));

        if (!searchByName) {
            return traitRepository.findAll(specification, pageable).map(traitMapper::CreateResponse);
        }

        String searchName = normalizeName(request.name());
        Specification<Trait> exactNameSpecification = specification.and((root, query, cb) ->
                cb.equal(root.<String>get("traitNameKey"), searchName));
        List<Trait> exactMatches = traitRepository.findAll(exactNameSpecification, PageRequest.of(0, 1)).getContent();
        if (!exactMatches.isEmpty()) {
            TraitResponseDto exactMatch = traitMapper.CreateResponse(exactMatches.get(0));
            return new PageImpl<>(List.of(exactMatch), PageRequest.of(0, request.getSize()), 1);
        }

        List<Trait> rankedTraits = traitRepository.findAll(specification).stream()
                .sorted(Comparator
                        .comparingInt((Trait trait) ->
                                trait.getTraitName().toLowerCase(Locale.ROOT).contains(searchName) ? 0 : 1)
                        .thenComparingInt(trait -> levenshteinDistance(
                                searchName, trait.getTraitName().toLowerCase(Locale.ROOT)))
                        .thenComparing(Trait::getTraitName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int start = (int) Math.min(pageable.getOffset(), rankedTraits.size());
        int end = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), rankedTraits.size());
        List<TraitResponseDto> pageContent = rankedTraits.subList(start, end).stream()
                .map(traitMapper::CreateResponse)
                .toList();
        return new PageImpl<>(pageContent, pageable, rankedTraits.size());
    }

    /** Builds a numeric filter only when a non-negative value was supplied; otherwise matches everything. */
    private static Specification<Trait> numericFilter(String field, String operator, Integer value) {
        if (value == null || value < 0) {
            return TraitSpecifications.all();
        }
        return TraitSpecifications.withNumericFilter(field, operator, value);
    }

    /** Loads a trait or throws 404 when it does not exist. */
    private Trait findTrait(UUID traitId) {
        return traitRepository.findById(traitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Trait not found with id: " + traitId));
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

    /** Creates a new category link for the given trait. */
    private static TraitCategories newLink(Trait trait, TraitCategory category, OffsetDateTime now) {
        return new TraitCategories(new TraitCategoriesId(trait.getId(), category), trait, category, now, now);
    }

    /** Removes nulls and duplicates from the requested categories while keeping their order. */
    private static Set<TraitCategory> distinctCategories(List<TraitCategory> categories) {
        if (categories == null) {
            return new LinkedHashSet<>();
        }
        return categories.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Normalizes a name the same way the database computes {@code trait_name_key}. */
    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** Returns the value, or 0 when it is null (matches the column defaults). */
    private static Integer orZero(Integer value) {
        return value == null ? 0 : value;
    }

    /** Returns the text, or "None" when it is blank (matches the column default). */
    private static String orNone(String value) {
        return value == null || value.isBlank() ? "None" : value;
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
