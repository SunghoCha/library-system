package msa.bookloan.application.saga.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.bookloan.application.saga.reply.point.PointChargeFailedInternalEvent;
import msa.bookloan.application.saga.reply.point.PointChargedInternalEvent;
import msa.bookloan.application.saga.reply.point.PointRefundedInternalEvent;
import msa.bookloan.domain.saga.LoanSaga;
import msa.common.events.bookloan.saga.command.ReleaseInventoryCommand;
import msa.common.events.bookloan.saga.command.ScheduleShippingCommand;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

import static java.time.LocalDateTime.now;
import static msa.bookloan.domain.saga.LoanSagaStep.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointStepService {

    private final Clock clock;
    private final Snowflake snowflake;
    private final SagaTimeouts sagaTimeouts;
    private final LoanSagaRepository sagaRepository;
    private final CommandOutboxRecorder commandOutboxRecorder;

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterPointChargeFailed(PointChargeFailedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(String.valueOf(event.sagaId())));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(POINT_CHARGING)) return;

        boolean entered = saga.enterCompensating(sagaTimeouts.compensationTimeoutFor(), now(clock));
        if (!entered) return;

        sagaRepository.saveAndFlush(saga);

        ReleaseInventoryCommand command = createReleaseInventoryCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 포인트 차징 실패: 보상 시작(ReleaseInventory). sagaId={}, reason={}",
                event.sagaId(), event.reasonCode());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterPointCharged(PointChargedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(String.valueOf(event.sagaId())));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(POINT_CHARGING)) return;

        boolean stepped = saga.markProcessing(SHIPPING_SCHEDULING, sagaTimeouts.stepTimeout(SHIPPING_SCHEDULING), now(clock));
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);

        ScheduleShippingCommand command = createScheduleShippingCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 배송 스케줄링 커맨드 발행 준비: sagaId={}, loanId={}",
                event.sagaId(), saga.getLoanId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterPointRefunded(PointRefundedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(String.valueOf(event.sagaId())));

        if (saga.isTerminal()) return;
        if (!saga.isCompensatingFrom(POINT_CHARGING)) return;

        boolean moved = saga.moveCompensatingTo(INVENTORY_RESERVING, sagaTimeouts.stepTimeout(INVENTORY_RESERVING), now(clock));
        if (!moved) return;

        sagaRepository.saveAndFlush(saga);

        ReleaseInventoryCommand command = createReleaseInventoryCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 보상 진행: 포인트 환불 완료 -> 재고 해제 발행. sagaId={}, loanId={}",
                event.sagaId(), saga.getLoanId());
    }

    private ScheduleShippingCommand createScheduleShippingCommand(LoanSaga saga, Long causationEventId) {
        return ScheduleShippingCommand.of(
                snowflake.nextId(),          // commandId
                saga.getId(),
                saga.getLoanId(),
                saga.getBookId(),
                causationEventId
        );
    }

    private ReleaseInventoryCommand createReleaseInventoryCommand(LoanSaga saga, Long causationEventId) {
        return ReleaseInventoryCommand.of(
                snowflake.nextId(),
                saga.getId(),
                saga.getLoanId(),
                saga.getBookId(),
                causationEventId
        );
    }

}
