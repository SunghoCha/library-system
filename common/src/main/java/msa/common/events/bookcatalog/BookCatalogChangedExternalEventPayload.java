package msa.common.events.bookcatalog;

import lombok.Builder;
import lombok.Getter;
import msa.common.domain.model.BookCategory;
import msa.common.events.DomainEventPayload;
import msa.common.events.EventType;

import java.time.LocalDateTime;

@Getter
public class BookCatalogChangedExternalEventPayload implements DomainEventPayload {
    private static final String AGGREGATE_TYPE = "BookCatalog";

    private String eventId;
    private EventType eventType;
    private String bookId;     // 책의 ID
    private String title;      // 책 제목
    private String author;     // 책 저자
    private BookCategory category;   // 책 카테고리
    private LocalDateTime occurredAt;

    @Builder
    public BookCatalogChangedExternalEventPayload(String eventId, EventType eventType,
                                                  String bookId, String title, String author,
                                                  BookCategory category, LocalDateTime occurredAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.category = category;
        this.occurredAt = occurredAt;
    }

    public static BookCatalogChangedExternalEventPayload of(BookCatalogChangedEvent event) {
        return BookCatalogChangedExternalEventPayload.builder()
                .eventId(String.valueOf(event.getEventId()))
                .eventType(event.getEventType())
                .bookId(event.getBookId())
                .title(event.getTitle())
                .author(event.getAuthor())
                .category(event.getCategory())
                .build();
    }

    @Override
    public String getAggregateId() {
        return bookId;
    }

    @Override
    public String getAggregateType() {
        return AGGREGATE_TYPE;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public BookCatalogChangedEvent toEvent() {
        return BookCatalogChangedEvent.builder()
                .eventId(Long.parseLong(eventId))
                .bookId(bookId)
                .title(title)
                .author(author)
                .category(category)
                .build();
    }
}
