package msa.bookloan.adapter.in.messaging.inbox.handler.projection;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.event.CatalogEventType;
import msa.bookloan.application.projection.BookCatalogProjectionProcessor;
import msa.common.events.bookcatalog.BookCatalogSnapshotPayload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookCatalogUpdatedInboxHandler implements InboxEventHandler<BookCatalogSnapshotPayload> {

    private final BookCatalogProjectionProcessor processor;

    @Override
    public String eventType() {
        return CatalogEventType.UPDATED.getValue();
    }

    @Override
    public Class<BookCatalogSnapshotPayload> payloadType() {
        return BookCatalogSnapshotPayload.class;
    }

    @Override
    public void handle(InboxMessage<BookCatalogSnapshotPayload> message) {
        BookCatalogSnapshotPayload payload = message.payload();
        Long aggregateVersion = message.aggregateVersion();
        Long eventId = message.eventId();
        processor.onUpdated(eventId, payload, aggregateVersion);
    }
}
