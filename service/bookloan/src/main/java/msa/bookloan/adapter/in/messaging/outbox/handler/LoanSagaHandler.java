package msa.bookloan.adapter.in.messaging.outbox.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
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
    private final BookLoanRepository bookLoanRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LoanRequestedInternalEvent event) {
        int bound = bookLoanRepository.tryBindSaga(event.loanId(), event.sagaId());
        if (bound == 0) {
            return;
        }
        orchestrator.start(event);
    }
}
