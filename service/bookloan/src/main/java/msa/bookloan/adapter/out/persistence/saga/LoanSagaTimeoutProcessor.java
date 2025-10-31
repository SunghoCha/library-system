package msa.bookloan.adapter.out.persistence.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.common.events.bookloan.saga.command.RefundPointCommand;
import msa.common.events.bookloan.saga.command.ReleaseInventoryCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaAbortReason;
import msa.bookloan.domain.saga.SagaStatus;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import static java.time.LocalDateTime.now;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanSagaTimeoutProcessor {

    private final Clock clock;
    private final Snowflake snowflake;
    private final SagaTimeouts sagaTimeouts;

    private final LoanSagaRepository sagaRepository;
    private final BookLoanRepository bookLoanRepository;
    private final CommandOutboxRecorder commandOutboxRecorder;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleProcessingTimeout(Long sagaId) {
        LoanSaga saga = sagaRepository.findForUpdate(sagaId) // 비관적 락
                .orElseThrow(() -> new SagaNotFoundException(String.valueOf(sagaId)));

        LocalDateTime now = now(clock);

        // 이미 마감 아니면 드롭
        if (saga.getStepDeadlineAt() == null || saga.getStepDeadlineAt().isAfter(now)) return;
        // 피벗 이후/터미널 방어
        if (saga.isAfterPivot() || saga.isTerminal()) return;

        Duration compTo = sagaTimeouts.compensationTimeoutFor();

        switch (saga.getCurrentStep()) {
            case INIT, MEMBER_CHECKING, INVENTORY_RESERVING -> {
                if (saga.markFailed(SagaAbortReason.TIMEOUT)) {
                    sagaRepository.save(saga);
                    bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getId());
                    log.info("[SagaTimeout] PROCESSING 타임아웃 처리: result=FAILED, sagaId={}, step={}, reason={}, at={}",
                            saga.getId(), saga.getCurrentStep(), SagaAbortReason.TIMEOUT, now);
                }
            }
            case POINT_CHARGING -> {
                if (saga.enterCompensating(compTo, now)) {
                    sagaRepository.save(saga);
                    commandOutboxRecorder.save(ReleaseInventoryCommand.of(
                            snowflake.nextId(), saga.getId(), saga.getLoanId(), saga.getBookId(), null));
                    log.info("[SagaTimeout] PROCESSING 타임아웃 처리: result=COMPENSATING, sagaId={}, fromStep={}, action=ReleaseInventory, deadline={}",
                            saga.getId(), LoanSagaStep.POINT_CHARGING, saga.getStepDeadlineAt());
                }
            }
            case SHIPPING_SCHEDULING, SHIPPING_ACCEPTED -> {
                if (saga.enterCompensating(compTo, now)) {
                    sagaRepository.save(saga);
                    commandOutboxRecorder.save(RefundPointCommand.of(
                            snowflake.nextId(), saga.getId(), saga.getLoanId(), saga.getMemberId(), null));
                    log.info("[SagaTimeout] PROCESSING 타임아웃 처리: result=COMPENSATING, sagaId={}, fromStep={}, action=RefundPoint, deadline={}",
                            saga.getId(), saga.getCurrentStep(), saga.getStepDeadlineAt());
                }
            }
            default -> {
                // 방어적 기본값: 실패 고정
                if (saga.markFailed(SagaAbortReason.TIMEOUT)) {
                    sagaRepository.save(saga);
                    bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getId());
                    log.info("[SagaTimeout] PROCESSING 타임아웃 처리: result=FAILED(default), sagaId={}, step={}, reason={}",
                            saga.getId(), saga.getCurrentStep(), SagaAbortReason.TIMEOUT);
                }
            }
        }

    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleCompensatingTimeout(Long sagaId) {
        LoanSaga saga = sagaRepository.findForUpdate(sagaId)
                .orElseThrow(() -> new SagaNotFoundException(String.valueOf(sagaId)));

        LocalDateTime now = now(clock);
        if (saga.getStatus() != SagaStatus.COMPENSATING) return;
        if (saga.getStepDeadlineAt() == null || saga.getStepDeadlineAt().isAfter(now)) return;

        Duration compTo = sagaTimeouts.compensationTimeoutFor();

        // 현재 보상 출발 스텝 기준으로 멱등 재발행 + 데드라인 연장
        if (saga.isCompensatingFrom(LoanSagaStep.POINT_CHARGING)) {
            saga.moveCompensatingTo(LoanSagaStep.POINT_CHARGING, compTo, now);
            sagaRepository.save(saga);
            commandOutboxRecorder.save(ReleaseInventoryCommand.of(
                    snowflake.nextId(), saga.getId(), saga.getLoanId(), saga.getBookId(), null));
            log.info("[SagaTimeout] COMPENSATING 재시도: sagaId={}, fromStep={}, action=ReleaseInventory, deadline={}",
                    saga.getId(), LoanSagaStep.POINT_CHARGING, saga.getStepDeadlineAt());
            return;
        }

        if (saga.isCompensatingFrom(LoanSagaStep.SHIPPING_SCHEDULING)
                || saga.isCompensatingFrom(LoanSagaStep.SHIPPING_ACCEPTED)) {
            saga.moveCompensatingTo(saga.getCurrentStep(), compTo, now);
            sagaRepository.save(saga);
            commandOutboxRecorder.save(RefundPointCommand.of(
                    snowflake.nextId(), saga.getId(), saga.getLoanId(), saga.getMemberId(), null));
            log.info("[SagaTimeout] COMPENSATING 재시도: sagaId={}, fromStep={}, action=RefundPoint, deadline={}",
                    saga.getId(), saga.getCurrentStep(), saga.getStepDeadlineAt());
            return;
        }

        // 정의되지 않은 보상 스텝이면 실패 고정(운영 점검 트리거)
        saga.markFailed(SagaAbortReason.TIMEOUT);
        sagaRepository.save(saga);
        bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getId());
        log.info("[SagaTimeout] COMPENSATING 타임아웃 처리: result=FAILED(default), sagaId={}, step={}, reason={}",
                saga.getId(), saga.getCurrentStep(), SagaAbortReason.TIMEOUT);

    }
}
