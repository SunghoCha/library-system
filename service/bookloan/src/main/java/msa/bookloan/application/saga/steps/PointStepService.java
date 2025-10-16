package msa.bookloan.application.saga.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.bookloan.application.saga.command.ReleaseInventoryCommand;
import msa.bookloan.application.saga.command.ScheduleShippingCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.bookloan.application.saga.reply.point.PointChargeFailedInternalEvent;
import msa.bookloan.application.saga.reply.point.PointChargedInternalEvent;
import msa.bookloan.application.saga.reply.point.PointRefundedInternalEvent;
import msa.bookloan.domain.saga.LoanSaga;
import msa.common.snowflake.Snowflake;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static msa.bookloan.domain.saga.LoanSagaStep.POINT_CHARGING;
import static msa.bookloan.domain.saga.LoanSagaStep.SHIPPING_SCHEDULING;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointStepService {

    private final Snowflake snowflake;
    private final SagaTimeouts sagaTimeouts;
    private final LoanSagaRepository sagaRepository;
    private final CommandOutboxRecorder commandOutboxRecorder;

    @Retryable(
            retryFor = {
                    QueryTimeoutException.class,
                    TransientDataAccessResourceException.class
            },
            noRetryFor = {
                    OptimisticLockingFailureException.class,
                    DataIntegrityViolationException.class
            },
            backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 800, random = true)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterPointChargeFailed(PointChargeFailedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(event.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(POINT_CHARGING)) return;

        boolean entered = saga.enterCompensating(sagaTimeouts.compensationTimeoutFor());
        if (!entered) return;

        sagaRepository.saveAndFlush(saga);

        ReleaseInventoryCommand command = createReleaseInventoryCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 포인트 차징 실패: 보상 시작(ReleaseInventory). sagaId={}, reason={}",
                event.sagaId(), event.payload().reasonCode());
    }

    @Retryable(
            retryFor = {
                    QueryTimeoutException.class,
                    TransientDataAccessResourceException.class
            },
            noRetryFor = {
                    OptimisticLockingFailureException.class,
                    DataIntegrityViolationException.class
            },
            backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 800, random = true)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterPointCharged(PointChargedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(event.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(POINT_CHARGING)) return;

        boolean stepped = saga.markProcessing(SHIPPING_SCHEDULING, sagaTimeouts.stepTimeout(SHIPPING_SCHEDULING));
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);

        ScheduleShippingCommand command = createScheduleShippingCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 배송 스케줄링 커맨드 발행 준비: sagaId={}, loanId={}",
                event.sagaId(), saga.getLoanId());
    }

    @Retryable(
            retryFor = {
                    QueryTimeoutException.class,
                    TransientDataAccessResourceException.class
            },
            noRetryFor = {
                    OptimisticLockingFailureException.class,
                    DataIntegrityViolationException.class
            },
            backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 800, random = true)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterPointRefunded(PointRefundedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(event.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isCompensatingFrom(SHIPPING_SCHEDULING)) return;

        boolean moved = saga.moveCompensatingTo(POINT_CHARGING, sagaTimeouts.stepTimeout(POINT_CHARGING));
        if (!moved) return;

        sagaRepository.saveAndFlush(saga);

        ReleaseInventoryCommand command = createReleaseInventoryCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 보상 진행: 포인트 환불 완료 -> 재고 해제 발행. sagaId={}, loanId={}",
                event.sagaId(), saga.getLoanId());
    }

    private ScheduleShippingCommand createScheduleShippingCommand(LoanSaga saga, Long causationEventId) {
        return ScheduleShippingCommand.builder()
                .commandId(snowflake.nextId())
                .sagaId(saga.getSagaId())
                .loanId(saga.getLoanId())
                .bookId(saga.getBookId())
                .sourceAggregateVersion(saga.getAggregateVersion())
                .causationEventId(causationEventId)
                .build();
    }

    private ReleaseInventoryCommand createReleaseInventoryCommand(LoanSaga saga, Long causationEventId) {
        return ReleaseInventoryCommand.builder()
                .commandId(snowflake.nextId())
                .sagaId(saga.getSagaId())
                .loanId(saga.getLoanId())
                .bookId(saga.getBookId())
                .sourceAggregateVersion(saga.getAggregateVersion())
                .causationEventId(causationEventId)
                .build();
    }

    @Recover
    public void recoverOnTransient(Exception ex, Object event) {
        log.warn("[Saga] 포인트 단계 재시도 소진. event={}, err={}", event, ex.getMessage(), ex);
    }
}
