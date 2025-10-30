package msa.bookloan.application.saga.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.application.event.LoanCancelRequestedInternalEvent;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanCancelSagaRequestListener {

    private final LoanRequestSagaOrchestrator orchestrator;
    private final BookLoanRepository bookLoanRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(LoanCancelRequestedInternalEvent event) {
        long loanId = event.loanId();
        String sagaId = bookLoanRepository.findCurrentSagaId(loanId).orElse(null);
        if (sagaId == null) {
            log.debug("[Saga] 취소 무시: loanId={}, reason={}, eventId={}",
                    event.loanId(), event.reason(), event.eventId());
            return;
        }

        boolean accepted = orchestrator.requestCancel(sagaId, event.reason(), event.eventId());
        log.info("[Saga] 취소 요청 처리 결과: loanId={}, sagaId={}, accepted={}",
                loanId, sagaId, accepted);

    }
}
