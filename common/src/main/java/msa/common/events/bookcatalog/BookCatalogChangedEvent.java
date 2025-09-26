package msa.common.events.bookcatalog;


import lombok.Builder;
import lombok.Getter;
import msa.common.domain.model.BookCategory;
import msa.common.events.DomainEvent;
import msa.common.events.EventType;

import java.time.LocalDateTime;

@Getter
public class BookCatalogChangedEvent implements DomainEvent {

    private final Long eventId;
    private final EventType eventType;
    private final long bookId;
    private final long aggregateVersion;
    private final String aggregateType;
    private final String title;
    private final String author;
    private final BookCategory category;
    private final LocalDateTime occurredAt;

    @Builder
    public BookCatalogChangedEvent(Long eventId, EventType eventType, long bookId, long aggregateVersion, String aggregateType,
                                   String title, String author, BookCategory category, LocalDateTime occurredAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.bookId = bookId;
        this.aggregateVersion = aggregateVersion;
        this.aggregateType = aggregateType;
        this.title = title;
        this.author = author;
        this.category = category;
        this.occurredAt = occurredAt;
    }


    @Override
    public long getAggregateId() {
        return bookId;
    }
}
