package msa.bookcatalog.infra.outbox.recorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
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
