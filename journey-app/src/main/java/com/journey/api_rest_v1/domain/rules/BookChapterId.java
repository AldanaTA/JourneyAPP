package com.journey.api_rest_v1.domain.rules;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Composite primary key for the {@code book_chapters} join table: a book includes each chapter at most once.
 */
@Embeddable
public class BookChapterId implements Serializable {

    @Column(name = "book_id", nullable = false, columnDefinition = "uuid")
    private UUID bookId;

    @Column(name = "chapter_id", nullable = false, columnDefinition = "uuid")
    private UUID chapterId;

    /** No-args constructor required by JPA. */
    public BookChapterId() {
    }

    /** Creates a key for the given book and chapter. */
    public BookChapterId(UUID bookId, UUID chapterId) {
        this.bookId = bookId;
        this.chapterId = chapterId;
    }

    public UUID getBookId() {
        return bookId;
    }

    public UUID getChapterId() {
        return chapterId;
    }

    /** Two keys are equal when both the book id and chapter id match. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookChapterId that = (BookChapterId) o;
        return Objects.equals(bookId, that.bookId) && Objects.equals(chapterId, that.chapterId);
    }

    /** Hash code consistent with {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return Objects.hash(bookId, chapterId);
    }
}
