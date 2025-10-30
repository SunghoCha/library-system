package msa.bookloan.application.saga.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.common.events.bookloan.saga.command.RefundPointCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.common.events.bookloan.saga.reply.shipping.ShippingAcceptedReply;
import msa.common.events.bookloan.saga.reply.shipping.ShippingScheduleFailedReply;
import msa.common.events.bookloan.saga.reply.shipping.ShippingScheduledReply;
import msa.bookloan.domain.saga.LoanSaga;
import msa.common.snowflake.Snowflake;
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
    @Transactional(propagation = Propagation.MANDATORY)
    public void afterShippingAccepted(ShippingAcceptedReply reply) {
        LoanSaga saga = sagaRepository.findById(reply.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(reply.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(SHIPPING_SCHEDULING)) return;

        boolean stepped = saga.markProcessing(SHIPPING_ACCEPTED, sagaTimeouts.stepTimeout(SHIPPING_ACCEPTED), now(clock));
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);
        log.info("[Saga] 배송 접수(Ack): sagaId={}, loanId={}", reply.sagaId(), saga.getLoanId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterShippingScheduled(ShippingScheduledReply event) {
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

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterShippingScheduleFailed(ShippingScheduleFailedReply reply) {
        LoanSaga saga = sagaRepository.findById(reply.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(reply.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAtAny(SHIPPING_SCHEDULING, SHIPPING_ACCEPTED)) return;

        boolean entered = saga.enterCompensating(sagaTimeouts.compensationTimeoutFor(), now(clock));
        if (!entered) return;

        sagaRepository.saveAndFlush(saga);

        RefundPointCommand command = createRefundPointCommand(saga, reply.eventId());
        commandOutboxRecorder.save(command);

        log.info("[Saga] 배송 스케줄 실패: 보상 시작(RefundPoint): sagaId={}, reason={}",
                reply.sagaId(), reply.reasonCode());
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

}
