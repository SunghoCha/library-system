package msa.bookloan.adapter.out.messaging.inbox.handler.saga.shipping;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.SagaReplyType;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedReply;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShippingScheduleFailedInboxHandler implements InboxEventHandler<ShippingScheduleFailedReply> {

    private final LoanRequestSagaOrchestrator orchestrator;

    @Override
    public String eventType() {
        return SagaReplyType.SHIPPING_SCHEDULE_FAILED.getValue();
    }

    @Override
    public Class<ShippingScheduleFailedReply> payloadType() {
        return ShippingScheduleFailedReply.class;
    }

    @Override
    public void handle(ShippingScheduleFailedReply payload) {
        orchestrator.onShippingScheduleFailed(payload);
    }
}
