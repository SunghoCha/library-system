package msa.bookloan.adapter.in.messaging.outbox.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.application.event.LoanCancelRequestedInternalEvent;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanCancelOutboxRecordHandler {

    private final LoanRequestSagaOrchestrator orchestrator;
    private final BookLoanRepository bookLoanRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(LoanCancelRequestedInternalEvent event) {
        Optional<String> sagaIdOpt = bookLoanRepository.findCurrentSagaId(event.loanId());
        if (sagaIdOpt.isEmpty()) {
            log.debug("[Saga] 취소 무시(in-flight 없음): loanId={}, reason={}, eventId={}",
                    event.loanId(), event.reason(), event.eventId());
            return;
        }

        try {
            orchestrator.requestCancel(sagaIdOpt.get(), event.reason(), event.eventId());
        } catch (RuntimeException ex) {
            // 특정 예외만 던져서 롤백할지 고민
            log.warn("[Saga] 취소 처리 중 예기치 못한 오류: loanId={}", event.loanId(), ex);

        }
    }
}
