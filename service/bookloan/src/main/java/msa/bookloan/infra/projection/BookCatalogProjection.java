package msa.bookloan.infra.projection;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;

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

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 200)
    private String author;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "category_name", length = 100, nullable = false)
    private String categoryName;

    @Column(name = "book_type", length = 32)
    private String bookType;

    @Column(name = "book_type_name", length = 32)
    private String bookTypeName;

    @Column(name = "aggregate_version", nullable = false)
    private Long aggregateVersion;

    @Column(name = "last_event_at")
    private LocalDateTime lastEventAt;

    @Version
    @Column(name = "row_version")
    private Long rowVersion;

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

    public boolean applySnapshot(BookCatalogChangedEvent e) {
        long incoming = e.getAggregateVersion();
        if (this.aggregateVersion != null && incoming <= this.aggregateVersion) return false;

        this.title = e.getTitle();
        this.author = e.getAuthor();
        this.categoryId = e.getCategory().categoryId();
        this.categoryName = e.getCategory().categoryName();
        this.bookType = e.getBookType().bookType();
        this.bookTypeName = e.getBookType().bookTypeName();
        this.aggregateVersion = incoming;
        this.lastEventAt = e.getOccurredAt();
        return true;
    }

    public static BookCatalogProjection from(BookCatalogChangedEvent e) {
        return BookCatalogProjection.builder()
                .bookId(e.getBookId())
                .title(e.getTitle())
                .author(e.getAuthor())
                .categoryId(e.getCategory().categoryId())
                .categoryName(e.getCategory().categoryName())
                .bookType(e.getBookType().bookType())
                .bookTypeName(e.getBookType().bookTypeName())
                .aggregateVersion(e.getAggregateVersion())
                .lastEventAt(e.getOccurredAt())
                .build();
    }
}
