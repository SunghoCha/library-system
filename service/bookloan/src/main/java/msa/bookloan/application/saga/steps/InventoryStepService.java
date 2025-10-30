package msa.bookloan.application.saga.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.common.events.bookloan.saga.command.ChargePointCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReleasedReply;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReserveFailedReply;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReservedReply;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.SagaAbortReason;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

import static java.time.LocalDateTime.now;
import static msa.bookloan.domain.saga.LoanSagaStep.INVENTORY_RESERVING;
import static msa.bookloan.domain.saga.LoanSagaStep.POINT_CHARGING;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryStepService {

    private final Clock clock;
    private final Snowflake snowflake;
    private final SagaTimeouts sagaTimeouts;
    private final LoanSagaRepository sagaRepository;
    private final BookLoanRepository bookLoanRepository;
    private final CommandOutboxRecorder commandOutboxRecorder;

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterInventoryReserved(InventoryReservedReply reply) {
        LoanSaga saga = sagaRepository.findById(reply.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(reply.sagaId()));

        if (saga.isTerminal()) {
            log.debug("[Saga] 종료된 사가로 처리 건너뜀: sagaId={}, status={}", saga.getSagaId(), saga.getStatus());
            return;
        }
        if (!saga.isProcessingAt(INVENTORY_RESERVING)) return;

        boolean stepped = saga.markProcessing(POINT_CHARGING, sagaTimeouts.stepTimeout(POINT_CHARGING), now(clock));
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);

        ChargePointCommand command = createChargePointCommand(saga, reply.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 포인트 차징 커맨드 발행 준비: sagaId={}, memberId={}",
                reply.sagaId(), saga.getMemberId());

    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterInventoryReserveFailed(InventoryReserveFailedReply reply) {
        LoanSaga saga = sagaRepository.findById(reply.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(reply.sagaId()));

        if (saga.isTerminal()) {
            log.debug("[Saga] 종료된 사가로 처리 건너뜀: sagaId={}, status={}", saga.getSagaId(), saga.getStatus());
            return;
        }
        if (!saga.isProcessingAt(INVENTORY_RESERVING)) return;

        boolean stepped = saga.markFailed(SagaAbortReason.INVENTORY_RESERVE_FAILED);
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);

        bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getSagaId());

        log.info("[Saga] 재고 예약 실패 처리 완료: sagaId={}, loanId={}, causeEventId={}, reason={}",
                reply.sagaId(), saga.getLoanId(), reply.eventId(), reply.reasonCode());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterInventoryReleased(InventoryReleasedReply reply) {
        LoanSaga saga = sagaRepository.findById(reply.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(reply.sagaId()));

        if (saga.isTerminal()) {
            log.debug("[Saga] 종료된 사가로 처리 건너뜀: sagaId={}, status={}", saga.getSagaId(), saga.getStatus());
            return;
        }
        if (!saga.isCompensatingFrom(POINT_CHARGING)) return;

        boolean stepped = saga.markFailed(SagaAbortReason.COMPENSATION);
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);
        bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getSagaId());
        log.info("[Saga] 보상 종료: 재고 해제 완료 → FAILED 확정, sagaId={}, loanId={}",
                reply.sagaId(), saga.getLoanId());
    }

    private ChargePointCommand createChargePointCommand(LoanSaga saga, Long causationEventId) {
        return ChargePointCommand.of(
                snowflake.nextId(),
                saga.getSagaId(),
                saga.getLoanId(),
                saga.getMemberId(),
                causationEventId
        );
    }

}
