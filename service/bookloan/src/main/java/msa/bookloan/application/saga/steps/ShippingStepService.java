package msa.bookloan.application.saga.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.bookloan.application.saga.command.RefundPointCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.bookloan.application.saga.reply.shipping.ShippingAcceptedInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledInternalEvent;
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

import java.time.Clock;

import static java.time.LocalDateTime.now;
import static msa.bookloan.domain.saga.LoanSagaStep.SHIPPING_ACCEPTED;
import static msa.bookloan.domain.saga.LoanSagaStep.SHIPPING_SCHEDULING;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingStepService {

    private final Clock clock;
    private final Snowflake snowflake;
    private final SagaTimeouts sagaTimeouts;
    private final LoanSagaRepository sagaRepository;
    private final BookLoanRepository bookLoanRepository;
    private final CommandOutboxRecorder commandOutboxRecorder;

    // 상태만 SHIPPING_ACCEPTED로 갱신하고 커맨드 보내지않음
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
    public void afterShippingAccepted(ShippingAcceptedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(event.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(SHIPPING_SCHEDULING)) return;

        boolean stepped = saga.markProcessing(SHIPPING_ACCEPTED, sagaTimeouts.stepTimeout(SHIPPING_ACCEPTED), now(clock));
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);
        log.info("[Saga] 배송 접수(Ack): sagaId={}, loanId={}", event.sagaId(), saga.getLoanId());
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
    public void afterShippingScheduled(ShippingScheduledInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(event.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAtAny(SHIPPING_SCHEDULING, SHIPPING_ACCEPTED)) return;

        boolean stepped = saga.markCompleted();
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);
        bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getSagaId());
        log.info("[Saga] 완료(ShippingScheduled): sagaId={}, loanId={}", event.sagaId(), saga.getLoanId());
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
    public void afterShippingScheduleFailed(ShippingScheduleFailedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(event.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAtAny(SHIPPING_SCHEDULING, SHIPPING_ACCEPTED)) return;

        boolean entered = saga.enterCompensating(sagaTimeouts.compensationTimeoutFor(), now(clock));
        if (!entered) return;

        sagaRepository.saveAndFlush(saga);

        RefundPointCommand command = createRefundPointCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 배송 스케줄 실패: 보상 시작(RefundPoint): sagaId={}, reason={}",
                event.sagaId(), event.payload().reasonCode());
    }

    private RefundPointCommand createRefundPointCommand(LoanSaga saga, Long causationEventId) {
        return RefundPointCommand.of(
                snowflake.nextId(),          // commandId
                saga.getSagaId(),
                saga.getLoanId(),
                saga.getMemberId(),
                causationEventId
        );
    }

    @Recover
    public void recoverOnTransient(Exception ex, Object event) {
        // 재시도 소진시 경고 로그
        log.warn("[Saga] 배송 단계 재시도 소진. event={}, err={}", event, ex.getMessage(), ex);
    }

}
