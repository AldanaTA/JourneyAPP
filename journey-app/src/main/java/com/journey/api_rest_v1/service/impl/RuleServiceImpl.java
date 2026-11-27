package com.journey.api_rest_v1.service.impl;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.domain.creatures.Creature;
import com.journey.api_rest_v1.domain.items.Item;
import com.journey.api_rest_v1.domain.jobs.Job;
import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.races.Race;
import com.journey.api_rest_v1.domain.rules.Asset;
import com.journey.api_rest_v1.domain.rules.Book;
import com.journey.api_rest_v1.domain.rules.BookChapter;
import com.journey.api_rest_v1.domain.rules.RuleChapter;
import com.journey.api_rest_v1.domain.rules.RuleSection;
import com.journey.api_rest_v1.domain.rules.RuleSectionEntity;
import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.dto.rules.RuleAssetDto;
import com.journey.api_rest_v1.dto.rules.RuleBookDto;
import com.journey.api_rest_v1.dto.rules.RuleChapterDto;
import com.journey.api_rest_v1.dto.rules.RuleChapterSummaryDto;
import com.journey.api_rest_v1.dto.rules.RuleSearchResultDto;
import com.journey.api_rest_v1.dto.rules.RuleSectionDto;
import com.journey.api_rest_v1.mapper.CreatureMapper;
import com.journey.api_rest_v1.mapper.ItemMapper;
import com.journey.api_rest_v1.mapper.JobMapper;
import com.journey.api_rest_v1.mapper.PowerMapper;
import com.journey.api_rest_v1.mapper.RaceMapper;
import com.journey.api_rest_v1.mapper.TraitMapper;
import com.journey.api_rest_v1.repository.AssetRepository;
import com.journey.api_rest_v1.repository.BookRepository;
import com.journey.api_rest_v1.repository.CreatureRepository;
import com.journey.api_rest_v1.repository.ItemRepository;
import com.journey.api_rest_v1.repository.JobRepository;
import com.journey.api_rest_v1.repository.PowerRepository;
import com.journey.api_rest_v1.repository.RaceRepository;
import com.journey.api_rest_v1.repository.RuleChapterRepository;
import com.journey.api_rest_v1.repository.RuleSectionEntityRepository;
import com.journey.api_rest_v1.service.campaign.ContentAccess;
import com.journey.api_rest_v1.repository.RuleSectionRepository;
import com.journey.api_rest_v1.repository.TraitRepository;
import com.journey.api_rest_v1.service.RuleService;

/**
 * Default {@link RuleService} implementation backed by JPA, with Redis caching for books and chapters.
 *
 * <p>A chapter is returned whole: its sections plus every entity and image their bodies link to, so the
 * reader renders it with one request. Entities are mapped with the compendium mappers, so they match what
 * {@code /api/v1/traits/{id}} and the other entity endpoints return.</p>
 *
 * <p>Rules are written by the rules importer straight to the database, which does not clear these caches;
 * re-imported chapters show up once the cached copy expires (the Redis TTL).</p>
 */
@Service
public class RuleServiceImpl implements RuleService {

    /** Who may read campaign rules (set by Spring). */
    private ContentAccess contentAccess;

    @org.springframework.beans.factory.annotation.Autowired
    void setContentAccess(ContentAccess contentAccess) {
        this.contentAccess = contentAccess;
    }

    private static final int MAX_SEARCH_RESULTS = 50;

    // Cleans search snippets, which ts_headline cuts from the Markdown body.
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\(asset:[^)]*\\)?");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\((?:trait|power|item|race|job|creature):[^)]*\\)?");
    private static final Pattern DANGLING_LINK = Pattern.compile("\\]?\\((?:trait|power|item|race|job|creature|asset):[^)\\s]*\\)?");
    private static final Pattern ESCAPE = Pattern.compile("\\\\(.)");
    private static final Pattern EMPHASIS = Pattern.compile("\\*+");
    private static final Pattern TABLE_RULE = Pattern.compile("\\|?\\s*:?-{3,}:?\\s*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final BookRepository bookRepository;
    private final RuleChapterRepository chapterRepository;
    private final RuleSectionRepository sectionRepository;
    private final RuleSectionEntityRepository sectionEntityRepository;
    private final AssetRepository assetRepository;
    private final TraitRepository traitRepository;
    private final PowerRepository powerRepository;
    private final ItemRepository itemRepository;
    private final RaceRepository raceRepository;
    private final JobRepository jobRepository;
    private final CreatureRepository creatureRepository;
    private final TraitMapper traitMapper;
    private final PowerMapper powerMapper;
    private final ItemMapper itemMapper;
    private final RaceMapper raceMapper;
    private final JobMapper jobMapper;
    private final CreatureMapper creatureMapper;

    /** Wires the rules repositories plus the entity repositories and mappers used to resolve references. */
    public RuleServiceImpl(BookRepository bookRepository,
            RuleChapterRepository chapterRepository,
            RuleSectionRepository sectionRepository,
            RuleSectionEntityRepository sectionEntityRepository,
            AssetRepository assetRepository,
            TraitRepository traitRepository,
            PowerRepository powerRepository,
            ItemRepository itemRepository,
            RaceRepository raceRepository,
            JobRepository jobRepository,
            CreatureRepository creatureRepository,
            TraitMapper traitMapper,
            PowerMapper powerMapper,
            ItemMapper itemMapper,
            RaceMapper raceMapper,
            JobMapper jobMapper,
            CreatureMapper creatureMapper) {
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
        this.sectionRepository = sectionRepository;
        this.sectionEntityRepository = sectionEntityRepository;
        this.assetRepository = assetRepository;
        this.traitRepository = traitRepository;
        this.powerRepository = powerRepository;
        this.itemRepository = itemRepository;
        this.raceRepository = raceRepository;
        this.jobRepository = jobRepository;
        this.creatureRepository = creatureRepository;
        this.traitMapper = traitMapper;
        this.powerMapper = powerMapper;
        this.itemMapper = itemMapper;
        this.raceMapper = raceMapper;
        this.jobMapper = jobMapper;
        this.creatureMapper = creatureMapper;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "rulesBooks", key = "'all'")
    public List<RuleBookDto> getBooks() {
        return bookRepository.findAllInLibraryOrder().stream().map(this::toBookDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "rulesBooksById", key = "#id")
    public RuleBookDto getBookById(String id) {
        Book book = bookRepository.findById(parseUuid(id, "book"))
                .orElseThrow(() -> notFound("Book", id));
        return toBookDto(book);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "rulesChapters", key = "#id")
    public RuleChapterDto getChapterById(String id) {
        RuleChapter chapter = chapterRepository.findById(parseUuid(id, "chapter"))
                .orElseThrow(() -> notFound("Chapter", id));

        List<RuleSection> sections = sectionRepository.findByChapterIdOrderBySortOrderAsc(chapter.getId());
        List<RuleSectionEntity> references = sections.isEmpty()
                ? List.of()
                : sectionEntityRepository.findBySectionIdIn(sections.stream().map(RuleSection::getId).toList());

        Map<String, RuleAssetDto> assets = new LinkedHashMap<>();
        for (Asset asset : assetRepository.findByChapterId(chapter.getId())) {
            assets.put(asset.getId().toString(), new RuleAssetDto(asset.getId().toString(),
                    asset.getContentType(), asset.getWidth(), asset.getHeight(), asset.getAltText()));
        }

        return new RuleChapterDto(
                chapter.getId().toString(),
                chapter.getTitle(),
                chapter.getNumber(),
                chapter.getDescription(),
                chapter.getSource().getSource_name(),
                sections.stream().map(RuleServiceImpl::toSectionDto).toList(),
                resolve(references, RuleSectionEntity::getTraitId, traitRepository, Trait::getId, traitMapper::CreateResponse),
                resolve(references, RuleSectionEntity::getPowerId, powerRepository, Power::getId, powerMapper::CreateResponse),
                resolve(references, RuleSectionEntity::getItemId, itemRepository, Item::getId, itemMapper::CreateResponse),
                resolve(references, RuleSectionEntity::getRaceId, raceRepository, Race::getId, raceMapper::CreateResponse),
                resolve(references, RuleSectionEntity::getJobId, jobRepository, Job::getId, jobMapper::CreateResponse),
                resolve(references, RuleSectionEntity::getCreatureId, creatureRepository, Creature::getId, creatureMapper::CreateResponse),
                assets);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuleSearchResultDto> search(String query, String bookId, int limit) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must not be blank");
        }
        String bookFilter = null;
        if (bookId != null && !bookId.isBlank()) {
            bookFilter = parseUuid(bookId, "book").toString();
        }
        int boundedLimit = Math.max(1, Math.min(limit, MAX_SEARCH_RESULTS));

        // Leave out the chapters of campaigns the caller is not a member of.
        String hidden = String.join(",", contentAccess.hiddenSources(ContentAccess.Kind.CHAPTER).stream().map(UUID::toString).toList());
        return sectionRepository.search(query.trim(), bookFilter, boundedLimit, hidden).stream()
                .map(row -> new RuleSearchResultDto(
                        row.getSectionId().toString(),
                        row.getSectionTitle(),
                        row.getSlug(),
                        row.getChapterId().toString(),
                        row.getChapterTitle(),
                        row.getBookId() == null ? null : row.getBookId().toString(),
                        cleanSnippet(row.getSnippet()),
                        row.getRank() == null ? 0 : row.getRank()))
                .toList();
    }

    // ----------------------------
    // Mapping
    // ----------------------------

    private RuleBookDto toBookDto(Book book) {
        List<RuleChapterSummaryDto> chapters = book.getChapters().stream()
                .map(RuleServiceImpl::toChapterSummary)
                .toList();
        return new RuleBookDto(book.getId().toString(), book.getTitle(), book.getDescription(),
                book.getSource().getSource_name(), chapters);
    }

    private static RuleChapterSummaryDto toChapterSummary(BookChapter entry) {
        RuleChapter chapter = entry.getChapter();
        return new RuleChapterSummaryDto(chapter.getId().toString(), chapter.getTitle(), chapter.getNumber(),
                entry.getSortOrder(), chapter.getSource().getSource_name());
    }

    private static RuleSectionDto toSectionDto(RuleSection section) {
        return new RuleSectionDto(
                section.getId().toString(),
                section.getParentId() == null ? null : section.getParentId().toString(),
                section.getTitle(),
                section.getLevel(),
                section.getSlug(),
                section.getBody(),
                section.getSortOrder());
    }

    /**
     * Loads the entities of one type referenced by a chapter (one query) and maps them, keyed by id.
     * Ids whose entity no longer exists are left out.
     */
    private static <E, D> Map<String, D> resolve(Collection<RuleSectionEntity> references,
            Function<RuleSectionEntity, UUID> idOf,
            JpaRepository<E, UUID> repository,
            Function<E, UUID> entityId,
            Function<E, D> mapper) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (RuleSectionEntity reference : references) {
            UUID id = idOf.apply(reference);
            if (id != null) {
                ids.add(id);
            }
        }
        Map<String, D> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (E entity : repository.findAllById(ids)) {
            result.put(entityId.apply(entity).toString(), mapper.apply(entity));
        }
        return result;
    }

    /** Turns a ts_headline fragment of a Markdown body into plain text (keeping the « » match markers). */
    public static String cleanSnippet(String snippet) {
        if (snippet == null) {
            return "";
        }
        String text = IMAGE.matcher(snippet).replaceAll(" ");
        text = LINK.matcher(text).replaceAll("$1");
        text = DANGLING_LINK.matcher(text).replaceAll(" ");
        text = TABLE_RULE.matcher(text).replaceAll(" ");
        text = EMPHASIS.matcher(text).replaceAll("");
        text = ESCAPE.matcher(text).replaceAll("$1");
        text = text.replace('|', ' ');
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    // ----------------------------
    // Helpers
    // ----------------------------

    private static UUID parseUuid(String id, String what) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + what + " id: " + id);
        }
    }

    private static ResponseStatusException notFound(String what, String id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found: " + id);
    }
}
