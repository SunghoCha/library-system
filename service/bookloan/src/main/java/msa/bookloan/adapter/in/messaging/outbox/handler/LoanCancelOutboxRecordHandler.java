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
        orchestrator.requestCancel(sagaIdOpt.get(), event.reason(), event.eventId());
    }
}
