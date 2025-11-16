package msa.bookcatalog.application.event;


import lombok.Builder;
import lombok.Getter;
import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;
import msa.common.events.EventType;
import msa.common.events.outbox.OutboxRecordableEvent;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
public class BookCatalogChangedEvent implements OutboxRecordableEvent {

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
    public Long eventId() {
        return eventId;
    }

    @Override
    public EventType eventType() {
        return eventType;
    }

    @Override
    public String aggregateType() {
        return AGGREGATE_TYPE;
    }

    @Override
    public Long aggregateId() {
        return bookId;
    }

    @Override
    public Long aggregateVersion() {
        return aggregateVersion;
    }

    @Override
    public LocalDateTime occurredAt() {
        return occurredAt;
    }

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
