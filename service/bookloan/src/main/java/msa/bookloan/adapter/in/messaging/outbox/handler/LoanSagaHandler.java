package msa.bookloan.adapter.in.messaging.outbox.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 애프터리스너 사용하면 스케줄러까지 발행경로 이분화되어서 별로 같음
@Deprecated
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanSagaHandler {

    private final LoanRequestSagaOrchestrator orchestrator;
    private final BookLoanRepository bookLoanRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LoanRequestedInternalEvent event) {
        long loanId = event.loanId();
        String sagaId = event.sagaId();

        log.debug("[Saga] LoanRequested 이벤트 수신: loanId={}, sagaId={}", loanId, sagaId);

        try {
            int bound = bookLoanRepository.tryBindSaga(event.loanId(), event.sagaId());
            if (bound == 0) {
                // 이미 바인딩되어 있거나 다른 스레드가 선점한 정상적인 스킵
                log.debug("[Saga] 바인딩 스킵(이미 바인딩 또는 경합 발생): loanId={}, sagaId={}", loanId, sagaId);
                return;
            }

            log.info("[Saga] 바인딩 성공. 오케스트레이터 시작: loanId={}, sagaId={}", loanId, sagaId);
            orchestrator.start(event);
            log.info("[Saga] 오케스트레이터 시작 완료: loanId={}, sagaId={}", loanId, sagaId);

        } catch (Exception e) {
            log.warn("[Saga] 오케스트레이터 시작 실패: loanId={}, sagaId={}", loanId, sagaId, e);
        }
    }
}
