package msa.bookloan.adapter.in.messaging.inbox.handler.saga.point;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.point.PointRefundedReply;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointRefundedInboxHandler implements InboxEventHandler<PointRefundedReply> {

    private final LoanRequestSagaOrchestrator orchestrator;

    @Override
    public String eventType() {
        return SagaReplyType.POINT_REFUNDED.getValue();
    }

    @Override
    public Class<PointRefundedReply> payloadType() {
        return PointRefundedReply.class;
    }

    @Override
    public void handle(InboxMessage<PointRefundedReply> message) {
        PointRefundedReply payload = message.payload();
        orchestrator.onPointRefunded(payload);
    }
}
