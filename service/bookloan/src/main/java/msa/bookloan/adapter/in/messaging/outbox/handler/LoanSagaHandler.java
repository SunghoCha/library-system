package msa.bookloan.adapter.in.messaging.outbox.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanSagaHandler {

    private final LoanRequestSagaOrchestrator orchestrator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LoanRequestedInternalEvent event) {
        try {
            orchestrator.start(event);
        } catch (Exception ex) {
            log.info("[Saga] 사가 시작 실패: sagaId={}, eventId={}", event.sagaId(), event.eventId(), ex);
        }
    }
}
