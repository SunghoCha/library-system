package msa.bookloan.application.saga.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.common.events.bookloan.saga.command.ReleaseInventoryCommand;
import msa.common.events.bookloan.saga.command.ScheduleShippingCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.common.events.bookloan.saga.reply.point.PointChargeFailedReply;
import msa.common.events.bookloan.saga.reply.point.PointChargedReply;
import msa.common.events.bookloan.saga.reply.point.PointRefundedReply;
import msa.bookloan.domain.saga.LoanSaga;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

import static java.time.LocalDateTime.now;
import static msa.bookloan.domain.saga.LoanSagaStep.POINT_CHARGING;
import static msa.bookloan.domain.saga.LoanSagaStep.SHIPPING_SCHEDULING;

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
    public void afterPointChargeFailed(PointChargeFailedReply reply) {
        LoanSaga saga = sagaRepository.findById(reply.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(reply.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(POINT_CHARGING)) return;

        boolean entered = saga.enterCompensating(sagaTimeouts.compensationTimeoutFor(), now(clock));
        if (!entered) return;

        sagaRepository.saveAndFlush(saga);

        ReleaseInventoryCommand command = createReleaseInventoryCommand(saga, reply.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 포인트 차징 실패: 보상 시작(ReleaseInventory). sagaId={}, reason={}",
                reply.sagaId(), reply.reasonCode());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterPointCharged(PointChargedReply reply) {
        LoanSaga saga = sagaRepository.findById(reply.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(reply.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(POINT_CHARGING)) return;

        boolean stepped = saga.markProcessing(SHIPPING_SCHEDULING, sagaTimeouts.stepTimeout(SHIPPING_SCHEDULING), now(clock));
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);

        ScheduleShippingCommand command = createScheduleShippingCommand(saga, reply.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 배송 스케줄링 커맨드 발행 준비: sagaId={}, loanId={}",
                reply.sagaId(), saga.getLoanId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterPointRefunded(PointRefundedReply reply) {
        LoanSaga saga = sagaRepository.findById(reply.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(reply.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isCompensatingFrom(SHIPPING_SCHEDULING)) return;

        boolean moved = saga.moveCompensatingTo(POINT_CHARGING, sagaTimeouts.stepTimeout(POINT_CHARGING), now(clock));
        if (!moved) return;

        sagaRepository.saveAndFlush(saga);

        ReleaseInventoryCommand command = createReleaseInventoryCommand(saga, reply.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 보상 진행: 포인트 환불 완료 -> 재고 해제 발행. sagaId={}, loanId={}",
                reply.sagaId(), saga.getLoanId());
    }

    private ScheduleShippingCommand createScheduleShippingCommand(LoanSaga saga, Long causationEventId) {
        return ScheduleShippingCommand.of(
                snowflake.nextId(),          // commandId
                saga.getSagaId(),
                saga.getLoanId(),
                saga.getBookId(),
                causationEventId
        );
    }

    private ReleaseInventoryCommand createReleaseInventoryCommand(LoanSaga saga, Long causationEventId) {
        return ReleaseInventoryCommand.of(
                snowflake.nextId(),
                saga.getSagaId(),
                saga.getLoanId(),
                saga.getBookId(),
                causationEventId
        );
    }

}
