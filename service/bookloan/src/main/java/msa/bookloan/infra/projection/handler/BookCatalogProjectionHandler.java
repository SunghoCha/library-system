package msa.bookloan.infra.projection.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.infra.inbox.recorder.EventRecorder;
import msa.bookloan.service.BookCatalogProjectionService;
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
public class BookCatalogProjectionHandler {

    private final BookCatalogProjectionService bookCatalogProjectionService;
    private final EventRecorder eventRecorder;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookCatalogUpdated(BookCatalogChangedEvent event) {
        Long eventId = event.getEventId();
        log.debug("catalog update start: BookCatalogUpdatedEvent [eventId={}, bookId={}]", eventId, event.getBookId());
        try {
            bookCatalogProjectionService.project(event);
            eventRecorder.recordSuccess(eventId);

            log.debug("catalog update success PROCESSED [eventId={}]", eventId);

        } catch (Exception e) {
            eventRecorder.recordFailure(eventId, e.getMessage());
            log.info("catalog update failed [eventId={}]: {}", eventId, e.getMessage(), e);
        }
    }

}
