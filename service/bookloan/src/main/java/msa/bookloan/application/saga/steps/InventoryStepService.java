package msa.bookloan.application.saga.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.bookloan.application.saga.command.ChargePointCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.bookloan.application.saga.reply.inventory.InventoryReleasedInternalEvent;
import msa.bookloan.application.saga.reply.inventory.InventoryReserveFailedInternalEvent;
import msa.bookloan.application.saga.reply.inventory.InventoryReservedInternalEvent;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.SagaAbortReason;
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

import static msa.bookloan.domain.saga.LoanSagaStep.INVENTORY_RESERVING;
import static msa.bookloan.domain.saga.LoanSagaStep.POINT_CHARGING;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryStepService {

    private final Snowflake snowflake;
    private final SagaTimeouts sagaTimeouts;
    private final LoanSagaRepository sagaRepository;
    private final BookLoanRepository bookLoanRepository;
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
    public void afterInventoryReserved(InventoryReservedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(event.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(INVENTORY_RESERVING)) return;

        boolean stepped = saga.markProcessing(POINT_CHARGING, sagaTimeouts.stepTimeout(POINT_CHARGING));
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);

        ChargePointCommand command = createChargePointCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 포인트 차징 커맨드 발행 준비: sagaId={}, memberId={}",
                event.sagaId(), saga.getMemberId());

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
    public void afterInventoryReserveFailed(InventoryReserveFailedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(event.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(INVENTORY_RESERVING)) return;

        boolean stepped = saga.markFailed(SagaAbortReason.INVENTORY_RESERVE_FAILED);
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);

        bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getSagaId());

        log.info("[Saga] 재고 예약 실패 처리 완료: sagaId={}, loanId={}, causeEventId={}, reason={}",
                event.sagaId(), saga.getLoanId(), event.eventId(), event.payload().reasonCode());
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
    public void afterInventoryReleased(InventoryReleasedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(event.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isCompensatingFrom(POINT_CHARGING)) return;

        boolean stepped = saga.markFailed(SagaAbortReason.COMPENSATION);
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);
        bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getSagaId());
        log.info("[Saga] 보상 종료: 재고 해제 완료 → FAILED 확정, sagaId={}, loanId={}",
                event.sagaId(), saga.getLoanId());
    }

    private ChargePointCommand createChargePointCommand(LoanSaga saga, Long causationEventId) {
        return ChargePointCommand.builder()
                .commandId(snowflake.nextId()) // 유니크한 커맨드가 만들어지는 지점. 이 메서드가 의도치않게 중복 실행되면 위험
                .sagaId(saga.getSagaId())
                .loanId(saga.getLoanId())
                .memberId(saga.getMemberId())
                .sourceAggregateVersion(saga.getAggregateVersion())
                .causationEventId(causationEventId)
                .build();
    }

    @Recover
    public void recoverOnTransient(Exception ex, Object event) {
        log.warn("[Saga] 재고 단계 재시도 소진. event={}, err={}", event, ex.getMessage(), ex);
    }
}
