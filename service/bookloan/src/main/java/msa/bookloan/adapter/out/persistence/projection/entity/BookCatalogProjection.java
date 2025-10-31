package msa.bookloan.adapter.out.persistence.projection.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import msa.bookloan.application.event.BookCatalogChangedEvent;
import msa.common.events.bookcatalog.BookCatalogSnapshotPayload;

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

    @Column(name = "aggregate_version", nullable = false) // BookCatalog 엔티티의 @Version
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

    public boolean applySnapshot(BookCatalogSnapshotPayload payload, Long incomingAggregateVersion) {
        if (this.aggregateVersion != null && incomingAggregateVersion <= this.aggregateVersion) return false;

        this.title = payload.title();
        this.author = payload.author();
        this.categoryId = payload.category().categoryId();
        this.categoryName = payload.category().categoryName();
        this.bookType = payload.bookType().bookType();
        this.bookTypeName = payload.bookType().bookTypeName();
        this.aggregateVersion = incomingAggregateVersion;
        this.lastEventAt = payload.occurredAt();
        return true;
    }

    public static BookCatalogProjection fromSnapshot(Long bookId, BookCatalogSnapshotPayload payload, Long aggregateVersion) {
        if (payload == null) {
            return null;
        }

        return BookCatalogProjection.builder()
                .bookId(bookId)
                .title(payload.title())
                .author(payload.author())
                .categoryId(payload.category().categoryId())
                .categoryName(payload.category().categoryName())
                .bookType(payload.bookType().bookType())
                .bookTypeName(payload.bookType().bookTypeName())
                .aggregateVersion(aggregateVersion) // 파라미터로 받은 version 사용
                .lastEventAt(payload.occurredAt())
                .build();
    }
}
