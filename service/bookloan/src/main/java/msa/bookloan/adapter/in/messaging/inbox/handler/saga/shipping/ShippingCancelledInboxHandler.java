package msa.bookloan.adapter.in.messaging.inbox.handler.saga.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.shipping.ShippingCancelledInternalEvent;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.shipping.ShippingCancelledReply;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingCancelledInboxHandler implements InboxEventHandler<ShippingCancelledReply> {

    private final LoanRequestSagaOrchestrator orchestrator;

    @Override
    public String eventType() {
        return SagaReplyType.SHIPPING_CANCELLED.getValue();
    }

    @Override
    public Class<ShippingCancelledReply> payloadType() {
        return ShippingCancelledReply.class;
    }

    @Override
    public void handle(InboxMessage<ShippingCancelledReply> message) {
        ShippingCancelledReply payload = message.payload();
        ShippingCancelledInternalEvent internalEvent = ShippingCancelledInternalEvent.from(payload);

        log.info("[SagaHandler][{}] 수신: sagaId={}, eventId={}",
                eventType(), internalEvent.sagaId(), message.eventId());

        orchestrator.onShippingCancelled(internalEvent);
    }
}
