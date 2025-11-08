package msa.bookloan.application.saga.listener;

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
public class LoanRequestSagaStartListener {

    private final LoanRequestSagaOrchestrator orchestrator;
    // 같은 로컬트랜잭션에서 사가 오케스트레이터 호출하는 구조
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(LoanRequestedInternalEvent event) {
        log.debug("[Saga] LoanRequested 이벤트 수신: loanId={}, sagaId={}", event.loanId(), event.sagaId());
        orchestrator.start(event);
    }
}
