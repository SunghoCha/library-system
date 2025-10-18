package msa.bookloan.adapter.out.persistence.projection.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import msa.bookloan.application.event.BookCatalogChangedEvent;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "book_catalog_projection",
        indexes = {
                @Index(name = "idx_category_id", columnList = "category_id")
        }
)
public class BookCatalogProjection {

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "author")
    private String author;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "book_type")
    private String bookType;

    @Column(name = "book_type_name")
    private String bookTypeName;

    @Column(name = "aggregate_version", nullable = false)
    private Long aggregateVersion;

    @Column(name = "last_event_at")
    private LocalDateTime lastEventAt;

    @Version
    @Column(name = "version")
    private Long version;

    @Builder
    public BookCatalogProjection(Long bookId, String title, String author,
                                 Integer categoryId, String categoryName,
                                 String bookType, String bookTypeName,
                                 Long aggregateVersion, LocalDateTime lastEventAt) {
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.bookType = bookType;
        this.bookTypeName = bookTypeName;
        this.aggregateVersion = aggregateVersion;
        this.lastEventAt = lastEventAt;
    }

    public boolean applySnapshot(BookCatalogChangedEvent event) {
        long incoming = event.getAggregateVersion();
        if (this.aggregateVersion != null && incoming <= this.aggregateVersion) return false;

        this.title = event.getTitle();
        this.author = event.getAuthor();
        this.categoryId = event.getCategory().categoryId();
        this.categoryName = event.getCategory().categoryName();
        this.bookType = event.getBookType().bookType();
        this.bookTypeName = event.getBookType().bookTypeName();
        this.aggregateVersion = incoming;
        this.lastEventAt = event.getOccurredAt();
        return true;
    }

    public static BookCatalogProjection from(BookCatalogChangedEvent event) {
        return BookCatalogProjection.builder()
                .bookId(event.getBookId())
                .title(event.getTitle())
                .author(event.getAuthor())
                .categoryId(event.getCategory().categoryId())
                .categoryName(event.getCategory().categoryName())
                .bookType(event.getBookType().bookType())
                .bookTypeName(event.getBookType().bookTypeName())
                .aggregateVersion(event.getAggregateVersion())
                .lastEventAt(event.getOccurredAt())
                .build();
    }
}
