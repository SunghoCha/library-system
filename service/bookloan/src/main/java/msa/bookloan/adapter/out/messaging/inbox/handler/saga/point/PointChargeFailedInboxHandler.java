package msa.bookloan.adapter.out.messaging.inbox.handler.saga.point;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.SagaReplyType;
import msa.bookloan.application.saga.reply.point.PointChargeFailedReply;
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
    public void handle(PointChargeFailedReply payload) {
        orchestrator.onPointChargeFailed(payload);
    }
}
