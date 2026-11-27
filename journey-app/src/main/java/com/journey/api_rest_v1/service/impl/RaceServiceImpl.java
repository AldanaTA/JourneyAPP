package com.journey.api_rest_v1.service.impl;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
import com.journey.api_rest_v1.domain.races.Race;
import com.journey.api_rest_v1.domain.races.RaceSize;
import com.journey.api_rest_v1.domain.races.RaceTraits;
import com.journey.api_rest_v1.domain.races.RaceTraitsId;
import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.domain.traits.TraitCategories;
import com.journey.api_rest_v1.domain.traits.TraitCategoriesId;
import com.journey.api_rest_v1.domain.traits.TraitCategory;
import com.journey.api_rest_v1.dto.race.CreateRaceRequest;
import com.journey.api_rest_v1.dto.race.RaceFilterRequest;
import com.journey.api_rest_v1.dto.race.RaceResponseDto;
import com.journey.api_rest_v1.mapper.RaceMapper;
import com.journey.api_rest_v1.repository.ContentSourceRepository;
import com.journey.api_rest_v1.repository.RaceRepository;
import com.journey.api_rest_v1.repository.RaceTraitsRepository;
import com.journey.api_rest_v1.repository.TraitRepository;
import com.journey.api_rest_v1.service.RaceService;
import com.journey.api_rest_v1.service.campaign.ContentAccess;
import com.journey.api_rest_v1.specification.SourceSpecifications;
import com.journey.api_rest_v1.specification.RaceSpecifications;

/**
 * Default {@link RaceService} implementation backed by JPA, with Redis caching for reads.
 * Follows the same structure as {@code JobServiceImpl}.
 *
 * <p>Racial traits are regular traits tagged {@link TraitCategory#RACIAL}. Linking a trait to a race
 * adds the tag; unlinking removes it once no race uses the trait. Because that changes trait (and
 * therefore job) payloads, every write also evicts the trait and job caches.</p>
 */
@Service
public class RaceServiceImpl implements RaceService {

    /** Who may read campaign content (set by Spring). */
    private ContentAccess contentAccess;

    @org.springframework.beans.factory.annotation.Autowired
    void setContentAccess(ContentAccess contentAccess) {
        this.contentAccess = contentAccess;
    }

    private static final String DEFAULT_SORT_FIELD = "raceName";
    private static final int DEFAULT_MOVEMENT = 30;
    private static final String DEFAULT_LANGUAGES = "Common";

    private final RaceRepository raceRepository;
    private final RaceTraitsRepository raceTraitsRepository;
    private final TraitRepository traitRepository;
    private final ContentSourceRepository contentSourceRepository;
    private final RaceMapper raceMapper;

    /** Wires the repositories and mapper this service depends on. */
    public RaceServiceImpl(RaceRepository raceRepository,
                           RaceTraitsRepository raceTraitsRepository,
                           TraitRepository traitRepository,
                           ContentSourceRepository contentSourceRepository,
                           RaceMapper raceMapper) {
        this.raceRepository = raceRepository;
        this.raceTraitsRepository = raceTraitsRepository;
        this.traitRepository = traitRepository;
        this.contentSourceRepository = contentSourceRepository;
        this.raceMapper = raceMapper;
    }

    /** Creates a race, rejecting duplicate names with 409, then links its traits. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"racesById", "racesAll", "traitsById", "traitsAll", "jobsById", "jobsAll", "creaturesById", "creaturesAll"},
            allEntries = true)
    public RaceResponseDto createRace(CreateRaceRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        ContentSource contentSource = findContentSource(request.content_source_id());
        List<Trait> traits = resolveTraits(request.trait_ids());

        if (raceRepository.existsByNameKey(normalizeName(request.race_name()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A race named '" + request.race_name().trim() + "' already exists");
        }

        Race race = new Race(
                null,
                request.race_name().trim(),
                request.min_race_size(),
                validMaxSize(request.min_race_size(), request.max_race_size()),
                orDefault(request.movement()),
                orCommon(request.languages()),
                blankToNull(request.race_description()),
                new ArrayList<>(),
                contentSource,
                now,
                now
        );

        Race savedRace = raceRepository.save(race);

        // The saved race is managed, so new links are persisted by cascade when the transaction flushes.
        addTraitLinks(savedRace, traits, now);

        return raceMapper.CreateResponse(savedRace);
    }

    /**
     * Updates every field of a race and replaces its trait links.
     * Links are cleared and flushed before re-adding, because the (race_id, sort_order)
     * unique constraint would otherwise collide mid-flush when reordering.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"racesById", "racesAll", "traitsById", "traitsAll", "jobsById", "jobsAll", "creaturesById", "creaturesAll"},
            allEntries = true)
    public RaceResponseDto updateRace(String id, CreateRaceRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID raceId = parseUuid(id, "race");
        Race existingRace = findRace(raceId);
        ContentSource contentSource = findContentSource(request.content_source_id());
        List<Trait> traits = resolveTraits(request.trait_ids());

        if (raceRepository.existsByNameKeyAndIdNot(normalizeName(request.race_name()), raceId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A race named '" + request.race_name().trim() + "' already exists");
        }

        Set<UUID> keptTraitIds = traits.stream().map(Trait::getId).collect(Collectors.toSet());
        List<Trait> removedTraits = existingRace.getTraits().stream()
                .map(RaceTraits::getTrait)
                .filter(trait -> !keptTraitIds.contains(trait.getId()))
                .toList();

        existingRace.setRaceName(request.race_name().trim());
        existingRace.setMinRaceSize(request.min_race_size());
        existingRace.setMaxRaceSize(validMaxSize(request.min_race_size(), request.max_race_size()));
        existingRace.setMovement(orDefault(request.movement()));
        existingRace.setLanguages(orCommon(request.languages()));
        existingRace.setRaceDescription(blankToNull(request.race_description()));
        existingRace.setSource(contentSource);
        existingRace.setUpdatedAt(now);

        existingRace.getTraits().clear();
        raceRepository.saveAndFlush(existingRace);

        addTraitLinks(existingRace, traits, now);
        removedTraits.forEach(trait -> untagIfUnused(trait, raceId));

        return raceMapper.CreateResponse(raceRepository.save(existingRace));
    }

    /** Deletes a race; its trait links are removed by cascade, and traits no other race uses lose RACIAL. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"racesById", "racesAll", "traitsById", "traitsAll", "jobsById", "jobsAll", "creaturesById", "creaturesAll"},
            allEntries = true)
    public String deleteRace(String id) {
        UUID raceId = parseUuid(id, "race");
        Race existingRace = findRace(raceId);
        String name = existingRace.getRaceName();
        List<Trait> traits = existingRace.getTraits().stream().map(RaceTraits::getTrait).toList();

        raceRepository.delete(existingRace);
        traits.forEach(trait -> untagIfUnused(trait, raceId));

        return "Race with id " + id + " (name: " + name + ") has been deleted.";
    }

    /** Returns one race by id, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "racesById", key = "#id")
    public RaceResponseDto getRaceById(String id) {
        return raceMapper.CreateResponse(findRace(parseUuid(id, "race")));
    }

    /** Returns a sorted page of all races, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "racesAll", key = "#page + ':' + #size + ':' + #sortBy + ':' + #sortDir")
    public Page<RaceResponseDto> getAllRaces(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        // Official content only, so this cached list is the same for everyone (campaign content: use the filter).
        return raceRepository.findAll(SourceSpecifications.<Race>notInSources(contentAccess.campaignSources()), pageable).map(raceMapper::CreateResponse);
    }

    /**
     * Applies every filter on the request. When a name is given, an exact match is returned alone;
     * otherwise results are ranked so names containing the search come first, then by edit distance.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<RaceResponseDto> filterRaces(RaceFilterRequest request) {
        Specification<Race> specification = RaceSpecifications.all()
                .and(SourceSpecifications.<Race>inSources(request.sourceIds()))
                .and(SourceSpecifications.<Race>notInSources(contentAccess.hiddenSources(ContentAccess.Kind.RACE)))
                .and(RaceSpecifications.withSizes(request.sizes()))
                .and(RaceSpecifications.withMovement(request.getMovementOperator(), request.movement()))
                .and(RaceSpecifications.withTraitName(request.traitName()));

        boolean searchByName = request.name() != null && !request.name().isBlank();
        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                searchByName ? Sort.unsorted() : Sort.by(Sort.Direction.ASC, DEFAULT_SORT_FIELD));

        if (!searchByName) {
            return raceRepository.findAll(specification, pageable).map(raceMapper::CreateResponse);
        }

        String searchName = normalizeName(request.name());
        List<Race> exactMatches = raceRepository
                .findAll(specification.and(RaceSpecifications.withExactName(searchName)), PageRequest.of(0, 1))
                .getContent();
        if (!exactMatches.isEmpty()) {
            RaceResponseDto exactMatch = raceMapper.CreateResponse(exactMatches.get(0));
            return new PageImpl<>(List.of(exactMatch), PageRequest.of(0, request.getSize()), 1);
        }

        List<Race> rankedRaces = raceRepository.findAll(specification).stream()
                .sorted(Comparator
                        .comparingInt((Race race) ->
                                race.getRaceName().toLowerCase(Locale.ROOT).contains(searchName) ? 0 : 1)
                        .thenComparingInt(race -> levenshteinDistance(
                                searchName, race.getRaceName().toLowerCase(Locale.ROOT)))
                        .thenComparing(Race::getRaceName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int start = (int) Math.min(pageable.getOffset(), rankedRaces.size());
        int end = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), rankedRaces.size());
        List<RaceResponseDto> pageContent = rankedRaces.subList(start, end).stream()
                .map(raceMapper::CreateResponse)
                .toList();
        return new PageImpl<>(pageContent, pageable, rankedRaces.size());
    }

    /** Attaches trait links (in request order) to a managed race and tags each trait RACIAL. */
    private static void addTraitLinks(Race race, List<Trait> traits, OffsetDateTime now) {
        for (int index = 0; index < traits.size(); index++) {
            Trait trait = traits.get(index);
            race.getTraits().add(new RaceTraits(
                    new RaceTraitsId(race.getId(), trait.getId()), race, trait, trait.getTraitName(), index, now, now));

            boolean tagged = trait.getCategories().stream()
                    .anyMatch(link -> link.getCategory() == TraitCategory.RACIAL);
            if (!tagged) {
                trait.getCategories().add(new TraitCategories(
                        new TraitCategoriesId(trait.getId(), TraitCategory.RACIAL), trait, TraitCategory.RACIAL,
                        now, now));
            }
        }
    }

    /** Removes the RACIAL tag from a trait that is no longer linked to any race other than {@code raceId}. */
    private void untagIfUnused(Trait trait, UUID raceId) {
        if (!raceTraitsRepository.existsByIdTraitIdAndIdRaceIdNot(trait.getId(), raceId)) {
            trait.getCategories().removeIf(link -> link.getCategory() == TraitCategory.RACIAL);
        }
    }

    /**
     * Loads the requested traits in request order.
     * Rejects malformed or repeated ids with 400 and unknown ids with 404.
     */
    private List<Trait> resolveTraits(List<String> traitIds) {
        if (traitIds == null || traitIds.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        for (String traitId : traitIds) {
            UUID id = parseUuid(traitId, "trait");
            if (!seen.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Trait id " + traitId + " is listed more than once");
            }
            ids.add(id);
        }

        Map<UUID, Trait> found = traitRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Trait::getId, Function.identity()));

        List<UUID> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Traits not found with ids: " + missing);
        }

        return ids.stream().map(found::get).toList();
    }

    /** Loads a race or throws 404 when it does not exist. */
    private Race findRace(UUID raceId) {
        return raceRepository.findById(raceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Race not found with id: " + raceId));
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

    /** Normalizes a name the same way the database computes {@code races.name_key} (trimmed, lowercase). */
    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the upper end of a size range: null when absent or equal to the minimum (a single size),
     * and a 400 when it is smaller than the minimum.
     */
    private static RaceSize validMaxSize(RaceSize minSize, RaceSize maxSize) {
        if (maxSize == null || maxSize == minSize) {
            return null;
        }
        if (maxSize.ordinal() < minSize.ordinal()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Max race size " + maxSize + " must not be smaller than min race size " + minSize);
        }
        return maxSize;
    }

    /** Returns the movement, or 30 when it is null (matches the column default). */
    private static Integer orDefault(Integer movement) {
        return movement == null ? DEFAULT_MOVEMENT : movement;
    }

    /** Returns the languages, or "Common" when blank (matches the column default). */
    private static String orCommon(String languages) {
        return languages == null || languages.isBlank() ? DEFAULT_LANGUAGES : languages.trim();
    }

    /** Returns the trimmed text, or null when blank (the description column is nullable). */
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
