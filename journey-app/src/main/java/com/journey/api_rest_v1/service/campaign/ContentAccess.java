package com.journey.api_rest_v1.service.campaign;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.service.CurrentUser;
import com.journey.api_rest_v1.service.impl.CharacterAccess;

/**
 * Who may read and write compendium content (traits, powers, items, creatures) and rules (books, chapters).
 *
 * <ul>
 *   <li>Official content (core rules and books): everyone signed in reads it; only admins write it.</li>
 *   <li>A campaign's own content: its members read it, its storytellers write it. Its creatures are read by the
 *       storytellers only, so players do not see the monsters ahead of time.</li>
 * </ul>
 * Admins read and write everything.
 */
@Component
public class ContentAccess {

    /** The content tables this guards, by kind. */
    public enum Kind {
        TRAIT("traits"), POWER("powers"), ITEM("items"), CREATURE("creatures"), RACE("races"), JOB("jobs"),
        BOOK("books"), CHAPTER("rule_chapters"), SHEET("character_sheets");

        private final String table;

        Kind(String table) {
            this.table = table;
        }
    }

    private static final Map<Boolean, String> HIDDEN_SQL = Map.of(
            false, hiddenSql("'owner', 'editor', 'viewer'"),
            true, hiddenSql("'owner', 'editor'"));

    private static String hiddenSql(String roles) {
        return """
                SELECT s.id FROM content_sources s
                WHERE CAST(s.kind AS TEXT) = 'campaign'
                  AND s.owner_user_id IS DISTINCT FROM :user
                  AND NOT EXISTS (SELECT 1 FROM source_members m WHERE m.source_id = s.id AND m.user_id = :user
                                  AND CAST(m.role AS TEXT) IN (%s))
                """.formatted(roles);
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;

    public ContentAccess(NamedParameterJdbcTemplate jdbc, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    /** Campaign sources whose content of this kind the caller may not read (empty for admins). */
    public List<UUID> hiddenSources(Kind kind) {
        if (currentUser.isAdmin()) return List.of();
        return jdbc.queryForList(HIDDEN_SQL.get(kind == Kind.CREATURE),
                new MapSqlParameterSource("user", currentUser.requireId()), UUID.class);
    }

    /** Every campaign source: the cached "all" lists show official content only, the same for everyone. */
    public List<UUID> campaignSources() {
        return jdbc.queryForList("SELECT id FROM content_sources WHERE CAST(kind AS TEXT) = 'campaign'",
                new MapSqlParameterSource(), UUID.class);
    }

    /** 404 unless the caller may read this entry (as if it did not exist). */
    public void requireReadable(Kind kind, String id) {
        UUID source = sourceOf(kind, id);
        if (source != null && hiddenSources(kind).contains(source)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
    }

    /**
     * 403 unless the caller may add or change content in this source: admins anywhere, a campaign's storytellers in
     * that campaign. A missing source is left to the endpoint for admins and refused for everyone else.
     */
    public void requireWritableSource(String sourceId) {
        if (currentUser.isAdmin()) return;
        if (sourceId == null || sourceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose the campaign this content belongs to (content_source_id)");
        }
        UUID source = CharacterAccess.parseUuid(sourceId, "content source");
        Boolean storyteller = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM content_sources s WHERE s.id = :source AND CAST(s.kind AS TEXT) = 'campaign'
                      AND (s.owner_user_id = :user OR EXISTS (
                          SELECT 1 FROM source_members m WHERE m.source_id = s.id AND m.user_id = :user
                            AND CAST(m.role AS TEXT) IN ('owner', 'editor'))))
                """, new MapSqlParameterSource("source", source).addValue("user", currentUser.requireId()), Boolean.class);
        if (!Boolean.TRUE.equals(storyteller)) {
            Boolean campaign = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM content_sources WHERE id = :source AND CAST(kind AS TEXT) = 'campaign')",
                    new MapSqlParameterSource("source", source), Boolean.class);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, Boolean.TRUE.equals(campaign)
                    ? "Only the campaign's storytellers can add or change its content"
                    : "Only admins can change official content; storytellers can add content to their own campaigns");
        }
    }

    /** 403 (or 404 when unreadable) unless the caller may change or delete this existing entry. */
    public void requireWritable(Kind kind, String id) {
        if (currentUser.isAdmin()) return;
        requireReadable(kind, id);
        UUID source = sourceOf(kind, id);
        if (source == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        requireWritableSource(source.toString());
    }

    /**
     * For races and jobs a storyteller makes: every trait must be official or the campaign's own (so another
     * campaign's traits are never shown to these players).
     */
    public void requireUsableTraits(String sourceId, java.util.List<String> traitIds) {
        if (currentUser.isAdmin() || traitIds == null || traitIds.isEmpty()) return;
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        for (String id : traitIds) ids.add(CharacterAccess.parseUuid(id, "trait"));
        Integer usable = jdbc.queryForObject("""
                SELECT COUNT(*) FROM traits t JOIN content_sources s ON s.id = t.source_id
                WHERE t.id IN (:ids) AND (CAST(s.kind AS TEXT) <> 'campaign' OR s.id = :source)
                """, new MapSqlParameterSource("ids", ids).addValue("source", CharacterAccess.parseUuid(sourceId, "content source")),
                Integer.class);
        if (usable == null || usable != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use official traits or this campaign's own traits");
        }
    }

    /** The source of an entry, or null when it does not exist (the endpoint then answers 404 as before). */
    private UUID sourceOf(Kind kind, String id) {
        UUID entry;
        try {
            entry = UUID.fromString(id.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        List<UUID> found = jdbc.queryForList("SELECT source_id FROM " + kind.table + " WHERE id = :id",
                new MapSqlParameterSource("id", entry), UUID.class);
        return found.isEmpty() ? null : found.get(0);
    }
}
