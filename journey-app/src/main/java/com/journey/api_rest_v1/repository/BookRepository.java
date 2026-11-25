package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.rules.Book;

/** Data access for rule books. */
@Repository
public interface BookRepository extends JpaRepository<Book, UUID> {

    /** Returns every book, core rules first, then by title. */
    @Query(value = """
            SELECT b.*
            FROM books b
            JOIN content_sources s ON s.id = b.source_id
            ORDER BY (s.kind = 'core') DESC, b.title_key
            """, nativeQuery = true)
    List<Book> findAllInLibraryOrder();
}
