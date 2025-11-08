package msa.bookloan.adapter.in.messaging.inbox.handler.saga.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledInternalEvent;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.shipping.ShippingScheduledReply;
import org.springframework.stereotype.Component;

@Slf4j
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
    public void handle(InboxMessage<ShippingScheduledReply> message) {
        ShippingScheduledReply payload = message.payload();
        ShippingScheduledInternalEvent internalEvent = ShippingScheduledInternalEvent.from(payload);

        log.info("[SagaHandler][{}] 수신: sagaId={}, eventId={}",
                eventType(), internalEvent.sagaId(), message.eventId());

        orchestrator.onShippingScheduled(internalEvent);
    }
}
