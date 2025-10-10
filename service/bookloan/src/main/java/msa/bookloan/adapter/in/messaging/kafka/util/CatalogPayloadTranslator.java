package msa.bookloan.adapter.in.messaging.kafka.util;

import msa.bookloan.application.event.BookCatalogChangedEvent;
import msa.common.events.EventType;
import msa.common.events.bookcatalog.BookCatalogChangedPayload;
import org.springframework.stereotype.Component;

@Component
public class CatalogPayloadTranslator {
    public BookCatalogChangedEvent toInternal(BookCatalogChangedPayload payload) {
        return BookCatalogChangedEvent.builder()
                .eventId(Long.parseLong(payload.eventId()))
                .eventType(parseEventType(payload.eventType()))
                .bookId(Long.parseLong(payload.bookId()))
                .aggregateVersion(payload.aggregateVersion())
                .title(payload.title())
                .author(payload.author())
                .category(payload.category())
                .bookType(payload.bookType())
                .occurredAt(payload.occurredAt())
                .build();
    }

    private EventType parseEventType(String s) {
        try {
            return EventType.valueOf(s);
        } catch (IllegalArgumentException ex) {
            // 임시
            return EventType.UNKNOWN;
        }
    }
}
