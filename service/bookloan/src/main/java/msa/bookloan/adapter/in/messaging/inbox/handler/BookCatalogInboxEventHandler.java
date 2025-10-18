package msa.bookloan.adapter.in.messaging.inbox.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.inbox.recorder.InboxAppender;
import msa.bookloan.application.event.BookCatalogChangedEvent;
import msa.bookloan.application.projection.BookCatalogProjectionProcessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookCatalogInboxEventHandler {

    private final BookCatalogProjectionProcessor bookCatalogProjectionProcessor;
    private final InboxAppender inboxAppender;

    // TODO : adaptor in -> out으로 가는 구조라 나중에 여유되면 중간에 서비스영역 거치도록 리팩토링...
    @Async("inboxExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookCatalogUpdated(BookCatalogChangedEvent event) {
        Long eventId = event.getEventId();
        log.debug("catalog update start: BookCatalogUpdatedEvent [eventId={}, bookId={}]", eventId, event.getBookId());
        try {
            bookCatalogProjectionProcessor.project(event);
            inboxAppender.recordSuccess(eventId);

            log.debug("catalog update success PROCESSED [eventId={}]", eventId);

        } catch (Exception e) {
            try {
                inboxAppender.recordFailure(eventId, e.getMessage());
                log.info("catalog update failed [eventId={}]: {}", eventId, e.getMessage(), e);
            } catch (Exception fatal) {
                log.warn("inbox recordFailure failed eventId={}", eventId, fatal);
            }

        }
    }

}
