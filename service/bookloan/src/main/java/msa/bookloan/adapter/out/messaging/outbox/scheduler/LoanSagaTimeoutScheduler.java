package msa.bookloan.adapter.out.messaging.outbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.saga.LoanSagaTimeoutProcessor;
import msa.bookloan.adapter.out.persistence.saga.LoanSagaTimeoutClaimerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanSagaTimeoutScheduler {

    private final LoanSagaTimeoutClaimerService timeoutClaimerService;
    private final LoanSagaTimeoutProcessor timeoutProcessor;

    @Scheduled(fixedDelayString = "${saga.timeout.processing.fixed-delay-ms:3000}")
    public void handleProcessingTimeouts() {
        List<Long> ids = timeoutClaimerService.claimProcessingTimeouts();
        if (ids.isEmpty()) return;

        log.info("[SagaTimeout] PROCESSING 처리 시작: count={}", ids.size());
        for (Long sagaId : ids) {
            try {
                timeoutProcessor.handleProcessingTimeout(sagaId);
            } catch (Exception e) {
                log.warn("[SagaTimeout] PROCESSING 처리 실패: sagaId={}, error={}", sagaId, e.toString(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${saga.timeout.compensating.fixed-delay-ms:5000}")
    public void handleCompensatingTimeouts() {
        List<Long> ids = timeoutClaimerService.claimCompensatingTimeouts();
        if (ids.isEmpty()) return;

        log.info("[SagaTimeout] COMPENSATING 처리 시작: count={}", ids.size());
        for (Long sagaId : ids) {
            try {
                timeoutProcessor.handleCompensatingTimeout(sagaId);
            } catch (Exception e) {
                log.warn("[SagaTimeout] COMPENSATING 처리 실패: sagaId={}, error={}", sagaId, e.toString(), e);
            }
        }
    }


}
