package msa.bookcatalog.infra.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookCatalogExternalEventPublisher {

    private final OutboxEventSender outboxEventSender;

    @Async("EVENT_ASYNC_TASK_EXECUTOR")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(BookCatalogChangedEvent event) {
        try {
            outboxEventSender.send(event);
            log.debug("즉시 발행 성공. eventId={}", event.getEventId());
        } catch (Exception e) {
            log.info("즉시 발행 실패. eventId={}, error='{}'", event.getEventId(), e.getMessage(), e);
        }
    }

}
