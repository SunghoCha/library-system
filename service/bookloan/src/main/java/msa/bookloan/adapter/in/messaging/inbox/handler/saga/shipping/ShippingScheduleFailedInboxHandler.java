package msa.bookloan.adapter.in.messaging.inbox.handler.saga.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedInternalEvent;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.shipping.ShippingScheduleFailedReply;
import org.springframework.stereotype.Component;

@Slf4j
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
    public void handle(InboxMessage<ShippingScheduleFailedReply> message) {
        ShippingScheduleFailedReply payload = message.payload();
        ShippingScheduleFailedInternalEvent internalEvent = ShippingScheduleFailedInternalEvent.from(payload);

        log.info("[SagaHandler][{}] 수신: sagaId={}, eventId={}",
                eventType(), internalEvent.sagaId(), message.eventId());

        orchestrator.onShippingScheduleFailed(internalEvent);
    }
}
