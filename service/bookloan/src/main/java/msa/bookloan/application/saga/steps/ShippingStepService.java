package msa.bookloan.application.saga.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.bookloan.application.saga.reply.shipping.ShippingAcceptedInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingCancelledInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledInternalEvent;
import msa.bookloan.domain.saga.LoanSaga;
import msa.common.events.bookloan.saga.command.RefundPointCommand;
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
public class ShippingStepService {

    private final Clock clock;
    private final Snowflake snowflake;
    private final SagaTimeouts sagaTimeouts;
    private final LoanSagaRepository sagaRepository;
    private final BookLoanRepository bookLoanRepository;
    private final CommandOutboxRecorder commandOutboxRecorder;

    // 상태만 SHIPPING_ACCEPTED로 갱신하고 커맨드 보내지않음
    @Transactional(propagation = Propagation.MANDATORY)
    public void afterShippingAccepted(ShippingAcceptedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(String.valueOf(event.sagaId())));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(SHIPPING_SCHEDULING)) return;

        boolean stepped = saga.markProcessing(SHIPPING_ACCEPTED, sagaTimeouts.stepTimeout(SHIPPING_ACCEPTED), now(clock));
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);
        log.info("[Saga] 배송 접수(Ack): sagaId={}, loanId={}", event.sagaId(), saga.getLoanId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterShippingScheduled(ShippingScheduledInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(String.valueOf(event.sagaId())));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAtAny(SHIPPING_SCHEDULING, SHIPPING_ACCEPTED)) return;

        boolean stepped = saga.markCompleted();
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);
        bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getId());
        log.info("[Saga] 완료(ShippingScheduled): sagaId={}, loanId={}", event.sagaId(), saga.getLoanId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterShippingScheduleFailed(ShippingScheduleFailedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(String.valueOf(event.sagaId())));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAtAny(SHIPPING_SCHEDULING, SHIPPING_ACCEPTED)) return;

        boolean entered = saga.enterCompensating(sagaTimeouts.compensationTimeoutFor(), now(clock));
        if (!entered) return;

        sagaRepository.saveAndFlush(saga);

        RefundPointCommand command = createRefundPointCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 배송 스케줄 실패: 보상 시작(RefundPoint): sagaId={}, reason={}",
                event.sagaId(), event.reasonCode());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterShippingCancelled(ShippingCancelledInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(String.valueOf(event.sagaId())));

        if (saga.isTerminal()) {
            return;
        }
        // 이 핸들러는 배송 구간에서 보상이 시작된 경우만 처리
        if (!saga.isCompensatingFrom(SHIPPING_SCHEDULING) && !saga.isCompensatingFrom(SHIPPING_ACCEPTED)) {
            return;
        }

        boolean moved = saga.moveCompensatingTo(
                POINT_CHARGING,
                sagaTimeouts.stepTimeout(POINT_CHARGING),
                now(clock)
        );
        if (!moved) {
            return;
        }

        sagaRepository.saveAndFlush(saga);

        RefundPointCommand command = createRefundPointCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 보상 진행(배송→포인트): 배송 취소 완료 -> 포인트 환불 발행. sagaId={}, loanId={}",
                event.sagaId(), saga.getLoanId());
    }

    private RefundPointCommand createRefundPointCommand(LoanSaga saga, Long causationEventId) {
        return RefundPointCommand.of(
                snowflake.nextId(),          // commandId
                saga.getId(),
                saga.getLoanId(),
                saga.getMemberId(),
                causationEventId
        );
    }

}
