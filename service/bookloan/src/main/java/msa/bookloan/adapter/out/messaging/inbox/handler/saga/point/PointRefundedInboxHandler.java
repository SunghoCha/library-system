package msa.bookloan.adapter.out.messaging.inbox.handler.saga.point;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.SagaReplyType;
import msa.bookloan.application.saga.reply.point.PointRefundedReply;
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
    public void handle(PointRefundedReply payload) {
        orchestrator.onPointRefunded(payload);
    }
}
