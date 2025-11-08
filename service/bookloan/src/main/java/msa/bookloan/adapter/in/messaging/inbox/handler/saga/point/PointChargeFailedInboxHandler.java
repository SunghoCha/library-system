package msa.bookloan.adapter.in.messaging.inbox.handler.saga.point;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.point.PointChargeFailedInternalEvent;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.point.PointChargeFailedReply;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointChargeFailedInboxHandler implements InboxEventHandler<PointChargeFailedReply> {

    private final LoanRequestSagaOrchestrator orchestrator;

    @Override
    public String eventType() {
        return SagaReplyType.POINT_CHARGE_FAILED.getValue();
    }

    @Override
    public Class<PointChargeFailedReply> payloadType() {
        return PointChargeFailedReply.class;
    }

    @Override
    public void handle(InboxMessage<PointChargeFailedReply> message) {
        PointChargeFailedReply payload = message.payload();
        PointChargeFailedInternalEvent internalEvent = PointChargeFailedInternalEvent.from(payload);

        log.info("[SagaHandler][{}] 수신: sagaId={}, eventId={}",
                eventType(), internalEvent.sagaId(), message.eventId());

        orchestrator.onPointChargeFailed(internalEvent);
    }
}
