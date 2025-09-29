package msa.common.events.bookcatalog;


import lombok.Builder;
import lombok.Getter;
import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;
import msa.common.events.DomainEvent;
import msa.common.events.EventType;

import java.time.LocalDateTime;

@Getter
public class BookCatalogChangedEvent implements DomainEvent {

    public static final String AGGREGATE_TYPE = "BookCatalog";

    private final Long eventId;
    private final EventType eventType;
    private final long bookId;
    private final long aggregateVersion;

    private final String title;
    private final String author;

    private final CategoryRef category;
    private final BookTypeRef bookType;

    private final LocalDateTime occurredAt;

    @Builder
    public BookCatalogChangedEvent(Long eventId, EventType eventType, long bookId, long aggregateVersion,
                                   String title, String author, CategoryRef category,
                                   BookTypeRef  bookType, LocalDateTime occurredAt) {
        this.eventId = eventId;
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
}
