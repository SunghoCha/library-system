package msa.common.events.bookcatalog;


import lombok.Builder;
import lombok.Getter;
import msa.common.domain.model.BookCategory;
import msa.common.events.DomainEvent;
import msa.common.events.EventType;

@Getter
public class BookCatalogChangedEvent implements DomainEvent {

    private final Long eventId;
    private final EventType eventType;
    private final String bookId;
    private final String title;
    private final String author;
    private final BookCategory category;

    @Builder
    public BookCatalogChangedEvent(Long eventId, EventType eventType, String bookId,
                                   String title, String author, BookCategory category) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.category = category;
    }


    @Override
    public String getAggregateId() {
        return bookId;
    }
}
