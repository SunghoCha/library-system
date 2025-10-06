package msa.bookloan.infra.messaging.inbox.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.infra.messaging.inbox.recorder.InboxAppender;
import msa.bookloan.application.projection.BookCatalogProjectionProcessor;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookCatalogInboxEventHandler {

    private final BookCatalogProjectionProcessor bookCatalogProjectionProcessor;
    private final InboxAppender inboxAppender;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookCatalogUpdated(BookCatalogChangedEvent event) {
        Long eventId = event.getEventId();
        log.debug("catalog update start: BookCatalogUpdatedEvent [eventId={}, bookId={}]", eventId, event.getBookId());
        try {
            bookCatalogProjectionProcessor.project(event);
            inboxAppender.recordSuccess(eventId);

            log.debug("catalog update success PROCESSED [eventId={}]", eventId);

        } catch (Exception e) {
            inboxAppender.recordFailure(eventId, e.getMessage());
            log.info("catalog update failed [eventId={}]: {}", eventId, e.getMessage(), e);
        }
    }

}
