package com.journey.api_rest_v1.service.campaign;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.dto.campaign.AddRuleChapterRequest;
import com.journey.api_rest_v1.dto.campaign.SaveChapterRequest;
import com.journey.api_rest_v1.dto.campaign.SaveRuleBookRequest;
import com.journey.api_rest_v1.dto.rules.RuleBookDto;
import com.journey.api_rest_v1.dto.rules.RuleChapterDto;
import com.journey.api_rest_v1.service.CurrentUser;
import com.journey.api_rest_v1.service.RuleService;
import com.journey.api_rest_v1.service.impl.CharacterAccess;

/**
 * A campaign's own rules: books in the campaign's content source, made of the campaign's chapters and, by
 * reference, official chapters (read-only here, kept up to date). Storytellers write them; members read them
 * through the usual /rules endpoints, which also serve the reader.
 */
@Service
public class CampaignRulesService {

    /** Entity links in a body, e.g. [Raise Dead](power:6f1c...). */
    private static final Pattern ENTITY_LINK = Pattern.compile(
            "\\]\\((trait|power|item|race|job|creature):([0-9a-fA-F-]{36})\\)");
    private static final Map<String, String> ENTITY_TABLES = Map.of("trait", "traits", "power", "powers",
            "item", "items", "race", "races", "job", "jobs", "creature", "creatures");

    private final NamedParameterJdbcTemplate jdbc;
    private final CampaignService campaigns;
    private final RuleService rules;
    private final CurrentUser currentUser;
    private final CacheManager cacheManager;

    public CampaignRulesService(NamedParameterJdbcTemplate jdbc, CampaignService campaigns, RuleService rules,
            CurrentUser currentUser, CacheManager cacheManager) {
        this.jdbc = jdbc;
        this.campaigns = campaigns;
        this.rules = rules;
        this.currentUser = currentUser;
        this.cacheManager = cacheManager;
    }

    // ----------------------------
    // Books
    // ----------------------------

    /** The campaign's books with their chapters (members). */
    @Transactional(readOnly = true)
    public List<RuleBookDto> books(UUID campaignId) {
        campaigns.requireMember(campaignId);
        return jdbc.queryForList("SELECT id FROM books WHERE source_id = :campaign ORDER BY title_key",
                p(campaignId), UUID.class).stream().map(id -> rules.getBookById(id.toString())).toList();
    }

    @Transactional
    public RuleBookDto createBook(UUID campaignId, SaveRuleBookRequest r) {
        campaigns.requireStoryteller(campaignId);
        if (r.title() == null || r.title().isBlank()) throw bad("A book needs a title");
        UUID id;
        try {
            id = jdbc.queryForObject("INSERT INTO books (source_id, title, description) VALUES (:campaign, :title, :description)"
                    + " RETURNING id", p(campaignId).addValue("title", r.title().trim()).addValue("description", blankToNull(r.description())),
                    UUID.class);
        } catch (DuplicateKeyException ex) {
            throw titleTaken(r.title());
        }
        return rules.getBookById(id.toString());
    }

    @Transactional
    public RuleBookDto updateBook(UUID campaignId, UUID bookId, SaveRuleBookRequest r) {
        campaigns.requireStoryteller(campaignId);
        requireOwnBook(campaignId, bookId);
        if (r.title() != null && r.title().isBlank()) throw bad("A book needs a title");
        try {
            jdbc.update("""
                    UPDATE books SET title = COALESCE(:title, title),
                           description = CASE WHEN :setDescription THEN :description ELSE description END, updated_at = NOW()
                    WHERE id = :book
                    """, p(campaignId).addValue("book", bookId).addValue("title", r.title() == null ? null : r.title().trim())
                    .addValue("setDescription", r.description() != null).addValue("description", blankToNull(r.description())));
        } catch (DuplicateKeyException ex) {
            throw titleTaken(r.title());
        }
        evictBook(bookId);
        return rules.getBookById(bookId.toString());
    }

    /** Deletes a book, and the campaign's chapters no other book uses (official chapters are only unlinked). */
    @Transactional
    public void deleteBook(UUID campaignId, UUID bookId) {
        campaigns.requireStoryteller(campaignId);
        requireOwnBook(campaignId, bookId);
        List<UUID> chapters = jdbc.queryForList("SELECT chapter_id FROM book_chapters WHERE book_id = :book",
                new MapSqlParameterSource("book", bookId), UUID.class);
        jdbc.update("DELETE FROM books WHERE id = :book", new MapSqlParameterSource("book", bookId));
        for (UUID chapter : chapters) {
            deleteIfUnused(campaignId, chapter);
        }
        evictBook(bookId);
    }

    // ----------------------------
    // Chapters in a book
    // ----------------------------

    /** Adds a new chapter, or an official chapter by reference, at the end of the book. */
    @Transactional
    public RuleBookDto addChapter(UUID campaignId, UUID bookId, AddRuleChapterRequest r) {
        campaigns.requireStoryteller(campaignId);
        requireOwnBook(campaignId, bookId);
        UUID chapterId;
        if (r.chapter_id() != null && !r.chapter_id().isBlank()) {
            chapterId = CharacterAccess.parseUuid(r.chapter_id(), "chapter");
            Boolean official = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM rule_chapters c JOIN content_sources s ON s.id = c.source_id
                                   WHERE c.id = :chapter AND (CAST(s.kind AS TEXT) <> 'campaign' OR s.id = :campaign))
                    """, p(campaignId).addValue("chapter", chapterId), Boolean.class);
            if (!Boolean.TRUE.equals(official)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapter not found");
            Boolean linked = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM book_chapters WHERE book_id = :book"
                    + " AND chapter_id = :chapter)", new MapSqlParameterSource("book", bookId).addValue("chapter", chapterId), Boolean.class);
            if (Boolean.TRUE.equals(linked)) throw new ResponseStatusException(HttpStatus.CONFLICT, "That chapter is already in this book");
        } else {
            if (r.title() == null || r.title().isBlank()) throw bad("A new chapter needs a title (or give chapter_id to add an existing one)");
            chapterId = jdbc.queryForObject("""
                    INSERT INTO rule_chapters (source_id, title, chapter_key, description)
                    VALUES (:campaign, :title, :key, :description) RETURNING id
                    """, p(campaignId).addValue("title", r.title().trim())
                    .addValue("key", "web-" + UUID.randomUUID()).addValue("description", blankToNull(r.description())), UUID.class);
        }
        jdbc.update("""
                INSERT INTO book_chapters (book_id, chapter_id, sort_order)
                VALUES (:book, :chapter, (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM book_chapters WHERE book_id = :book))
                """, new MapSqlParameterSource("book", bookId).addValue("chapter", chapterId));
        evictBook(bookId);
        return rules.getBookById(bookId.toString());
    }

    /** Takes a chapter out of the book; the campaign's own chapter is deleted when no other book uses it. */
    @Transactional
    public RuleBookDto removeChapter(UUID campaignId, UUID bookId, UUID chapterId) {
        campaigns.requireStoryteller(campaignId);
        requireOwnBook(campaignId, bookId);
        if (jdbc.update("DELETE FROM book_chapters WHERE book_id = :book AND chapter_id = :chapter",
                new MapSqlParameterSource("book", bookId).addValue("chapter", chapterId)) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That chapter is not in this book");
        }
        deleteIfUnused(campaignId, chapterId);
        evictBook(bookId);
        return rules.getBookById(bookId.toString());
    }

    @Transactional
    public RuleBookDto reorderChapters(UUID campaignId, UUID bookId, List<String> chapterIds) {
        campaigns.requireStoryteller(campaignId);
        requireOwnBook(campaignId, bookId);
        Set<UUID> current = new HashSet<>(jdbc.queryForList("SELECT chapter_id FROM book_chapters WHERE book_id = :book",
                new MapSqlParameterSource("book", bookId), UUID.class));
        List<UUID> order = new ArrayList<>(new LinkedHashSet<>(chapterIds.stream().map(id -> CharacterAccess.parseUuid(id, "chapter")).toList()));
        if (order.size() != chapterIds.size() || !current.equals(new HashSet<>(order))) {
            throw bad("List every chapter of the book exactly once");
        }
        for (int i = 0; i < order.size(); i++) {
            jdbc.update("UPDATE book_chapters SET sort_order = :order, updated_at = NOW() WHERE book_id = :book AND chapter_id = :chapter",
                    new MapSqlParameterSource("book", bookId).addValue("chapter", order.get(i)).addValue("order", i + 1));
        }
        evictBook(bookId);
        return rules.getBookById(bookId.toString());
    }

    // ----------------------------
    // Chapter content
    // ----------------------------

    /**
     * Saves a whole chapter of the campaign: title, description, and every section. Changed sections keep their
     * previous version in the revision history; entity links in the bodies are recorded for the reader's cards
     * (only to official content or this campaign's own).
     */
    @Transactional
    public RuleChapterDto saveChapter(UUID campaignId, UUID chapterId, SaveChapterRequest r) {
        campaigns.requireStoryteller(campaignId);
        Boolean own = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM rule_chapters WHERE id = :chapter AND source_id = :campaign)",
                p(campaignId).addValue("chapter", chapterId), Boolean.class);
        if (!Boolean.TRUE.equals(own)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not one of this campaign's chapters (official chapters cannot be edited here)");
        }
        MapSqlParameterSource chapter = new MapSqlParameterSource("chapter", chapterId);
        if (r.title() != null && r.title().isBlank()) throw bad("A chapter needs a title");
        jdbc.update("""
                UPDATE rule_chapters SET title = COALESCE(:title, title),
                       description = CASE WHEN :setDescription THEN :description ELSE description END, updated_at = NOW()
                WHERE id = :chapter
                """, chapter.addValue("title", r.title() == null ? null : r.title().trim())
                .addValue("setDescription", r.description() != null).addValue("description", blankToNull(r.description())));

        if (r.sections() != null) saveSections(campaignId, chapterId, r.sections());
        evictChapter(chapterId);
        return rules.getChapterById(chapterId.toString());
    }

    private void saveSections(UUID campaignId, UUID chapterId, List<SaveChapterRequest.Section> sections) {
        MapSqlParameterSource chapter = new MapSqlParameterSource("chapter", chapterId);
        record Existing(String title, String body) {
        }
        Map<UUID, Existing> existing = new HashMap<>();
        jdbc.query("SELECT id, title, body FROM rule_sections WHERE chapter_id = :chapter", chapter, rs -> {
            existing.put(UUID.fromString(rs.getString("id")), new Existing(rs.getString("title"), rs.getString("body")));
        });
        Set<UUID> kept = new HashSet<>();
        for (SaveChapterRequest.Section s : sections) {
            if (s.id() == null || s.id().isBlank()) continue;
            UUID id = CharacterAccess.parseUuid(s.id(), "section");
            if (!existing.containsKey(id)) throw bad("Section " + id + " is not in this chapter");
            if (!kept.add(id)) throw bad("Section " + id + " is listed twice");
        }

        // Unhook the tree and the slugs first, so removing, moving, and renaming sections cannot collide.
        jdbc.update("UPDATE rule_sections SET parent_id = NULL, slug = 'tmp-' || id WHERE chapter_id = :chapter", chapter);
        jdbc.update("DELETE FROM rule_section_entities WHERE section_id IN (SELECT id FROM rule_sections WHERE chapter_id = :chapter)", chapter);
        for (UUID id : existing.keySet()) {
            if (!kept.contains(id)) jdbc.update("DELETE FROM rule_sections WHERE id = :id", new MapSqlParameterSource("id", id));
        }

        Deque<UUID[]> parents = new ArrayDeque<>(); // [section id, level]
        Map<UUID, Integer> levels = new HashMap<>();
        Set<String> slugs = new HashSet<>();
        UUID editor = currentUser.requireId();
        int order = 0;
        for (SaveChapterRequest.Section s : sections) {
            String title = s.title().trim();
            String body = s.body() == null ? "" : s.body();
            while (!parents.isEmpty() && levels.get(parents.peek()[0]) >= s.level()) parents.pop();
            UUID parent = parents.isEmpty() ? null : parents.peek()[0];
            String slug = uniqueSlug(title, slugs);
            MapSqlParameterSource row = new MapSqlParameterSource("chapter", chapterId).addValue("parent", parent)
                    .addValue("title", title).addValue("level", s.level()).addValue("slug", slug).addValue("body", body)
                    .addValue("order", ++order);
            UUID id;
            if (s.id() == null || s.id().isBlank()) {
                id = jdbc.queryForObject("""
                        INSERT INTO rule_sections (chapter_id, parent_id, title, level, slug, body, sort_order)
                        VALUES (:chapter, :parent, :title, :level, :slug, :body, :order) RETURNING id
                        """, row, UUID.class);
            } else {
                id = UUID.fromString(s.id().trim());
                Existing before = existing.get(id);
                if (!before.title().equals(title) || !before.body().equals(body)) {
                    jdbc.update("""
                            INSERT INTO rule_section_revisions (section_id, title, body, edited_by_user_id, note)
                            VALUES (:id, :title, :body, :editor, 'Edited in the web app')
                            """, new MapSqlParameterSource("id", id).addValue("title", before.title())
                            .addValue("body", before.body()).addValue("editor", editor));
                }
                jdbc.update("""
                        UPDATE rule_sections SET parent_id = :parent, title = :title, level = :level, slug = :slug, body = :body,
                               sort_order = :order, updated_at = NOW()
                        WHERE id = :id
                        """, row.addValue("id", id));
            }
            levels.put(id, s.level());
            parents.push(new UUID[] {id});
            recordLinks(campaignId, id, body);
        }
    }

    /** Records the entities a body links to (official content or this campaign's own; other links are ignored). */
    private void recordLinks(UUID campaignId, UUID sectionId, String body) {
        Matcher m = ENTITY_LINK.matcher(body);
        Set<String> seen = new HashSet<>();
        int order = 0;
        while (m.find()) {
            String kind = m.group(1);
            UUID entity;
            try {
                entity = UUID.fromString(m.group(2));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (!seen.add(kind + entity)) continue;
            Boolean allowed = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM %s e JOIN content_sources s ON s.id = e.source_id
                                   WHERE e.id = :entity AND (CAST(s.kind AS TEXT) <> 'campaign' OR s.id = :campaign))
                    """.formatted(ENTITY_TABLES.get(kind)), p(campaignId).addValue("entity", entity), Boolean.class);
            if (!Boolean.TRUE.equals(allowed)) continue;
            jdbc.update("INSERT INTO rule_section_entities (section_id, %s_id, sort_order) VALUES (:section, :entity, :order)"
                    .formatted(kind), new MapSqlParameterSource("section", sectionId).addValue("entity", entity).addValue("order", order++));
        }
    }

    // ----------------------------
    // Helpers
    // ----------------------------

    private void requireOwnBook(UUID campaignId, UUID bookId) {
        Boolean own = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM books WHERE id = :book AND source_id = :campaign)",
                p(campaignId).addValue("book", bookId), Boolean.class);
        if (!Boolean.TRUE.equals(own)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found in this campaign");
    }

    private void deleteIfUnused(UUID campaignId, UUID chapterId) {
        jdbc.update("""
                DELETE FROM rule_chapters c WHERE c.id = :chapter AND c.source_id = :campaign
                  AND NOT EXISTS (SELECT 1 FROM book_chapters bc WHERE bc.chapter_id = c.id)
                """, p(campaignId).addValue("chapter", chapterId));
        evictChapter(chapterId);
    }

    /** "TP Brackets" -> "tp-brackets", then "tp-brackets-2" if taken in this chapter. */
    static String uniqueSlug(String title, Set<String> taken) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "section";
        if (base.length() > 80) base = base.substring(0, 80);
        String slug = base;
        for (int n = 2; !taken.add(slug); n++) slug = base + "-" + n;
        return slug;
    }

    private void evictChapter(UUID chapterId) {
        evict("rulesChapters", chapterId);
        for (UUID book : jdbc.queryForList("SELECT book_id FROM book_chapters WHERE chapter_id = :chapter",
                new MapSqlParameterSource("chapter", chapterId), UUID.class)) {
            evict("rulesBooksById", book);
        }
    }

    private void evictBook(UUID bookId) {
        evict("rulesBooksById", bookId);
    }

    private void evict(String cacheName, UUID key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) cache.evict(key.toString());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException titleTaken(String title) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "There is already a book called "
                + Objects.requireNonNullElse(title, "").trim() + "; pick another title");
    }

    private MapSqlParameterSource p(UUID campaignId) {
        return new MapSqlParameterSource("campaign", campaignId).addValue("user", currentUser.requireId());
    }
}
