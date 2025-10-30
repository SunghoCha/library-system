package msa.bookloan.adapter.in.messaging.inbox.handler.projection;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.event.CatalogEventType;
import msa.bookloan.application.projection.BookCatalogProjectionProcessor;
import msa.common.events.bookcatalog.BookCatalogDeletedPayload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookCatalogDeletedInboxHandler implements InboxEventHandler<BookCatalogDeletedPayload> {

    private final BookCatalogProjectionProcessor processor;

    @Override
    public String eventType() {
        return CatalogEventType.DELETED.getValue();
    }

    @Override
    public Class<BookCatalogDeletedPayload> payloadType() {
        return BookCatalogDeletedPayload.class;
    }

    @Override
    public void handle(InboxMessage<BookCatalogDeletedPayload> message) {
        BookCatalogDeletedPayload payload = message.payload();
        Long eventId = message.eventId();
        Long aggregateVersion = message.aggregateVersion();
        processor.onDeleted(eventId, payload, aggregateVersion);
    }
}
