package msa.bookloan.adapter.in.messaging.inbox.handler.saga.point;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.point.PointChargeFailedReply;
import org.springframework.stereotype.Component;

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
        orchestrator.onPointChargeFailed(payload);
    }
}
