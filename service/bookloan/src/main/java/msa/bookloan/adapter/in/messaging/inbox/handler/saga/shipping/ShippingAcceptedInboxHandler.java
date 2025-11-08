package msa.bookloan.adapter.in.messaging.inbox.handler.saga.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.shipping.ShippingAcceptedInternalEvent;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.shipping.ShippingAcceptedReply;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingAcceptedInboxHandler implements InboxEventHandler<ShippingAcceptedReply> {

    private final LoanRequestSagaOrchestrator orchestrator;

    @Override
    public String eventType() {
        return SagaReplyType.SHIPPING_ACCEPTED.getValue();
    }

    @Override
    public Class<ShippingAcceptedReply> payloadType() {
        return ShippingAcceptedReply.class;
    }

    @Override
    public void handle(InboxMessage<ShippingAcceptedReply> message) {
        ShippingAcceptedReply payload = message.payload();
        ShippingAcceptedInternalEvent internalEvent = ShippingAcceptedInternalEvent.from(payload);

        log.info("[SagaHandler][{}] 수신: sagaId={}, eventId={}",
                eventType(), internalEvent.sagaId(), message.eventId());

        orchestrator.onShippingAccepted(internalEvent);
    }
}
