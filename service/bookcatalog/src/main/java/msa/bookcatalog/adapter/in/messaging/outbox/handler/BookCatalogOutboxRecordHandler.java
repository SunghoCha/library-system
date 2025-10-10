package msa.bookcatalog.adapter.in.messaging.outbox.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.adapter.out.persistence.outbox.EventRecorder;
import msa.bookcatalog.application.event.BookCatalogChangedEvent;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
public class BookCatalogOutboxRecordHandler {

    private final EventRecorder eventRecorder;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleBookCatalogUpdate(BookCatalogChangedEvent event) {
        eventRecorder.save(event);
    }
}
