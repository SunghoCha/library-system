package msa.bookloan.adapter.out.messaging.inbox.handler.saga.shipping;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.SagaReplyType;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledReply;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShippingScheduledInboxHandler implements InboxEventHandler<ShippingScheduledReply> {

    private final LoanRequestSagaOrchestrator orchestrator;

    @Override
    public String eventType() {
        return SagaReplyType.SHIPPING_SCHEDULED.getValue();
    }

    @Override
    public Class<ShippingScheduledReply> payloadType() {
        return ShippingScheduledReply.class;
    }

    @Override
    public void handle(ShippingScheduledReply payload) {
        orchestrator.onShippingScheduled(payload);
    }
}
