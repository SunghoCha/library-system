package msa.bookloan.adapter.in.messaging.outbox.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.outbox.recorder.EventRecorder;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanOutboxRecordHandler {

    private final EventRecorder eventRecorder;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onLoanRequested(LoanRequestedInternalEvent event) {
        eventRecorder.save(event); // 내부에서 RoutingResolver로 topic/partitionKey 결정
    }
}
