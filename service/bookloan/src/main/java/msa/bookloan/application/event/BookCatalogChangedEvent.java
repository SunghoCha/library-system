package msa.bookloan.application.event;


import lombok.Builder;
import lombok.Getter;
import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;
import msa.common.events.DomainEvent;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
public class BookCatalogChangedEvent implements DomainEvent {

    public static final String AGGREGATE_TYPE = "BookCatalog";

    private final Long eventId;
    private final String eventType;
    private final long bookId;
    private final long aggregateVersion;

    private final String title;
    private final String author;

    private final CategoryRef category;
    private final BookTypeRef bookType;

    private final LocalDateTime occurredAt;

    @Builder
    public BookCatalogChangedEvent(Long eventId, String eventType, long bookId, long aggregateVersion,
                                   String title, String author, CategoryRef category,
                                   BookTypeRef  bookType, LocalDateTime occurredAt) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.eventType = eventType;
        this.bookId = bookId;
        this.aggregateVersion = aggregateVersion;
        this.title = title;
        this.author = author;
        this.category = category;
        this.bookType = bookType;
        this.occurredAt = occurredAt;
    }

    @Override
    public long getAggregateId() {
        return bookId;
    }

    @Override
    public String getAggregateType() { return AGGREGATE_TYPE; }

    @Override
    public long getAggregateVersion() { return aggregateVersion; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookCatalogChangedEvent that = (BookCatalogChangedEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(eventId);
    }
}
