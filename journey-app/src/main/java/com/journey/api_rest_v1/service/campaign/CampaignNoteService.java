package com.journey.api_rest_v1.service.campaign;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.dto.campaign.NoteDto;
import com.journey.api_rest_v1.dto.campaign.NoteSearchResultDto;
import com.journey.api_rest_v1.dto.campaign.NoteTreeDto;
import com.journey.api_rest_v1.dto.campaign.SaveFolderRequest;
import com.journey.api_rest_v1.dto.campaign.SaveNoteRequest;
import com.journey.api_rest_v1.service.CurrentUser;
import com.journey.api_rest_v1.service.impl.CharacterAccess;
import com.journey.api_rest_v1.service.impl.RuleServiceImpl;

/** The storytellers' campaign notes, in folders. Players never see them. */
@Service
public class CampaignNoteService {

    private static final int MAX_SEARCH_RESULTS = 30;

    private final NamedParameterJdbcTemplate jdbc;
    private final CampaignService campaigns;
    private final CurrentUser currentUser;

    public CampaignNoteService(NamedParameterJdbcTemplate jdbc, CampaignService campaigns, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.campaigns = campaigns;
        this.currentUser = currentUser;
    }

    /** Every folder and note title (bodies are loaded one note at a time). */
    @Transactional(readOnly = true)
    public NoteTreeDto tree(UUID campaignId) {
        campaigns.requireStoryteller(campaignId);
        MapSqlParameterSource p = params(campaignId);
        List<NoteTreeDto.FolderDto> folders = jdbc.query("""
                SELECT id, parent_id, name, sort_order FROM campaign_note_folders WHERE campaign_id = :campaign
                ORDER BY sort_order, name
                """, p, (rs, i) -> new NoteTreeDto.FolderDto(rs.getString("id"), rs.getString("parent_id"),
                        rs.getString("name"), rs.getInt("sort_order")));
        List<NoteTreeDto.NoteSummaryDto> notes = jdbc.query("""
                SELECT id, folder_id, title, sort_order, updated_at FROM campaign_notes WHERE campaign_id = :campaign
                ORDER BY sort_order, title
                """, p, (rs, i) -> new NoteTreeDto.NoteSummaryDto(rs.getString("id"), rs.getString("folder_id"),
                        rs.getString("title"), rs.getInt("sort_order"), String.valueOf(rs.getObject("updated_at"))));
        return new NoteTreeDto(folders, notes);
    }

    @Transactional(readOnly = true)
    public List<NoteSearchResultDto> search(UUID campaignId, String query) {
        campaigns.requireStoryteller(campaignId);
        if (query == null || query.isBlank()) return List.of();
        return jdbc.query("""
                SELECT n.id, n.folder_id, n.title,
                       ts_headline('english', n.body, q, 'StartSel=«, StopSel=», MaxWords=35, MinWords=12, MaxFragments=1') AS snippet
                FROM campaign_notes n, websearch_to_tsquery('english', :query) q
                WHERE n.campaign_id = :campaign AND n.search_vector @@ q
                ORDER BY ts_rank(n.search_vector, q) DESC, n.title
                LIMIT :limit
                """, params(campaignId).addValue("query", query.trim()).addValue("limit", MAX_SEARCH_RESULTS),
                (rs, i) -> new NoteSearchResultDto(rs.getString("id"), rs.getString("folder_id"), rs.getString("title"),
                        RuleServiceImpl.cleanSnippet(rs.getString("snippet"))));
    }

    @Transactional(readOnly = true)
    public NoteDto get(UUID campaignId, UUID noteId) {
        campaigns.requireStoryteller(campaignId);
        try {
            return jdbc.queryForObject("""
                    SELECT n.*, a.display_name AS author_name, e.display_name AS editor_name FROM campaign_notes n
                    LEFT JOIN users a ON a.id = n.author_user_id LEFT JOIN users e ON e.id = n.edited_by_user_id
                    WHERE n.campaign_id = :campaign AND n.id = :note
                    """, params(campaignId).addValue("note", noteId),
                    (rs, i) -> new NoteDto(rs.getString("id"), rs.getString("folder_id"), rs.getString("title"),
                            rs.getString("body"), rs.getInt("sort_order"), rs.getString("author_name"),
                            rs.getString("editor_name"), String.valueOf(rs.getObject("created_at")),
                            String.valueOf(rs.getObject("updated_at"))));
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found");
        }
    }

    @Transactional
    public NoteDto createNote(UUID campaignId, SaveNoteRequest r) {
        campaigns.requireStoryteller(campaignId);
        if (r.title() == null || r.title().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A note needs a title");
        UUID id = write(() -> jdbc.queryForObject("""
                INSERT INTO campaign_notes (campaign_id, folder_id, title, body, sort_order, author_user_id, edited_by_user_id)
                VALUES (:campaign, :folder, :title, :body, :sort, :user, :user) RETURNING id
                """, params(campaignId).addValue("folder", folder(r.folder_id())).addValue("title", r.title().trim())
                .addValue("body", r.body() == null ? "" : r.body()).addValue("sort", r.sort_order() == null ? 0 : r.sort_order()),
                UUID.class));
        return get(campaignId, id);
    }

    @Transactional
    public NoteDto updateNote(UUID campaignId, UUID noteId, SaveNoteRequest r) {
        campaigns.requireStoryteller(campaignId);
        if (r.title() != null && r.title().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A note needs a title");
        int updated = write(() -> jdbc.update("""
                UPDATE campaign_notes SET
                    folder_id = CASE WHEN :moveFolder THEN CAST(:folder AS uuid) ELSE folder_id END,
                    title = COALESCE(:title, title), body = COALESCE(:body, body), sort_order = COALESCE(:sort, sort_order),
                    edited_by_user_id = :user, updated_at = NOW()
                WHERE campaign_id = :campaign AND id = :note
                """, params(campaignId).addValue("note", noteId).addValue("moveFolder", r.folder_id() != null)
                .addValue("folder", folder(r.folder_id())).addValue("title", r.title() == null ? null : r.title().trim())
                .addValue("body", r.body()).addValue("sort", r.sort_order())));
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found");
        return get(campaignId, noteId);
    }

    @Transactional
    public void deleteNote(UUID campaignId, UUID noteId) {
        campaigns.requireStoryteller(campaignId);
        if (jdbc.update("DELETE FROM campaign_notes WHERE campaign_id = :campaign AND id = :note",
                params(campaignId).addValue("note", noteId)) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found");
        }
    }

    @Transactional
    public NoteTreeDto createFolder(UUID campaignId, SaveFolderRequest r) {
        campaigns.requireStoryteller(campaignId);
        if (r.name() == null || r.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A folder needs a name");
        write(() -> jdbc.update("""
                INSERT INTO campaign_note_folders (campaign_id, parent_id, name, sort_order, author_user_id)
                VALUES (:campaign, :parent, :name, :sort, :user)
                """, params(campaignId).addValue("parent", folder(r.parent_id())).addValue("name", r.name().trim())
                .addValue("sort", r.sort_order() == null ? 0 : r.sort_order())));
        return tree(campaignId);
    }

    @Transactional
    public NoteTreeDto updateFolder(UUID campaignId, UUID folderId, SaveFolderRequest r) {
        campaigns.requireStoryteller(campaignId);
        if (r.name() != null && r.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A folder needs a name");
        int updated = write(() -> jdbc.update("""
                UPDATE campaign_note_folders SET
                    parent_id = CASE WHEN :move THEN CAST(:parent AS uuid) ELSE parent_id END,
                    name = COALESCE(:name, name), sort_order = COALESCE(:sort, sort_order), updated_at = NOW()
                WHERE campaign_id = :campaign AND id = :folderId
                """, params(campaignId).addValue("folderId", folderId).addValue("move", r.parent_id() != null)
                .addValue("parent", folder(r.parent_id())).addValue("name", r.name() == null ? null : r.name().trim())
                .addValue("sort", r.sort_order())));
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found");
        return tree(campaignId);
    }

    /** Deletes a folder with everything in it. */
    @Transactional
    public NoteTreeDto deleteFolder(UUID campaignId, UUID folderId) {
        campaigns.requireStoryteller(campaignId);
        if (jdbc.update("DELETE FROM campaign_note_folders WHERE campaign_id = :campaign AND id = :folderId",
                params(campaignId).addValue("folderId", folderId)) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found");
        }
        return tree(campaignId);
    }

    /** A folder id from a request: "" (or null) is the top level. */
    private static UUID folder(String value) {
        return value == null || value.isBlank() ? null : CharacterAccess.parseUuid(value, "folder");
    }

    /** Runs a write and turns the database's refusals into readable 400s. */
    private static <T> T write(java.util.function.Supplier<T> work) {
        try {
            return work.get();
        } catch (DataIntegrityViolationException ex) {
            String message = String.valueOf(ex.getMostSpecificCause().getMessage());
            if (message.contains("cannot be moved into itself")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A folder cannot be moved into itself or one of its own folders");
            }
            if (message.contains("fk_campaign_notes_folder") || message.contains("fk_campaign_note_folders_parent")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That folder is not in this campaign");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That change is not allowed");
        }
    }

    private MapSqlParameterSource params(UUID campaignId) {
        return new MapSqlParameterSource("campaign", campaignId).addValue("user", currentUser.requireId());
    }
}
