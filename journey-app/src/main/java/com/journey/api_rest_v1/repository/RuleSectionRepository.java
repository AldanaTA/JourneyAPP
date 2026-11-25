package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.rules.RuleSection;

/** Data access for rule sections, including full-text search. */
@Repository
public interface RuleSectionRepository extends JpaRepository<RuleSection, UUID> {

    /** Returns a chapter's sections in reading order. */
    List<RuleSection> findByChapterIdOrderBySortOrderAsc(UUID chapterId);

    /** One row of {@link #search}. */
    interface SearchRow {
        UUID getSectionId();

        String getSectionTitle();

        String getSlug();

        UUID getChapterId();

        String getChapterTitle();

        UUID getBookId();

        String getSnippet();

        Double getRank();
    }

    /**
     * Full-text search over section titles and bodies, best matches first. The query uses web-search syntax
     * ("prone -stand", "\"opportunity attack\""). When bookId is set, only chapters in that book are searched;
     * bookId in the result is the book the chapter belongs to (that book, or the first book listing it).
     */
    @Query(value = """
            SELECT s.id AS sectionId,
                   s.title AS sectionTitle,
                   s.slug AS slug,
                   c.id AS chapterId,
                   c.title AS chapterTitle,
                   (SELECT bc.book_id
                      FROM book_chapters bc
                     WHERE bc.chapter_id = c.id
                       AND (CAST(:bookId AS uuid) IS NULL OR bc.book_id = CAST(:bookId AS uuid))
                     ORDER BY bc.book_id
                     LIMIT 1) AS bookId,
                   ts_headline('english', s.body, q,
                               'StartSel=«, StopSel=», MaxWords=35, MinWords=12, MaxFragments=1') AS snippet,
                   CAST(ts_rank(s.search_vector, q) AS double precision) AS rank
            FROM rule_sections s
            JOIN rule_chapters c ON c.id = s.chapter_id,
                 websearch_to_tsquery('english', :query) q
            WHERE s.search_vector @@ q
              AND (CAST(:bookId AS uuid) IS NULL OR EXISTS (
                    SELECT 1 FROM book_chapters bc
                    WHERE bc.chapter_id = c.id AND bc.book_id = CAST(:bookId AS uuid)))
            ORDER BY rank DESC, c.title, s.sort_order
            LIMIT :limit
            """, nativeQuery = true)
    List<SearchRow> search(@Param("query") String query, @Param("bookId") String bookId, @Param("limit") int limit);
}
