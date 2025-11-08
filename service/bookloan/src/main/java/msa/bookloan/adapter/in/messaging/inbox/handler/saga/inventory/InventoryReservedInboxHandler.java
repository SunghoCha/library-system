package msa.bookloan.adapter.in.messaging.inbox.handler.saga.inventory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.inventory.InventoryReservedInternalEvent;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReservedReply;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReservedInboxHandler implements InboxEventHandler<InventoryReservedReply> {

    private final LoanRequestSagaOrchestrator orchestrator;

    @Override
    public String eventType() {
        return SagaReplyType.INVENTORY_RESERVED.getValue();
    }

    @Override
    public Class<InventoryReservedReply> payloadType() {
        return InventoryReservedReply.class;
    }

    @Override
    public void handle(InboxMessage<InventoryReservedReply> message) {
        InventoryReservedReply payload = message.payload();
        InventoryReservedInternalEvent internalEvent = InventoryReservedInternalEvent.from(payload);

        log.info("[SagaHandler][{}] 수신: sagaId={}, eventId={}",
                eventType(), internalEvent.sagaId(), message.eventId());

        orchestrator.onInventoryReserved(internalEvent);
    }
}
