package msa.bookloan.adapter.in.messaging.inbox.handler.saga.inventory;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.inventory.InventoryReserveFailedInternalEvent;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReserveFailedReply;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryReserveFailedInboxHandler implements InboxEventHandler<InventoryReserveFailedReply> {

    private final LoanRequestSagaOrchestrator orchestrator;

    @Override
    public String eventType() {
        return SagaReplyType.INVENTORY_RESERVE_FAILED.getValue();
    }

    @Override
    public Class<InventoryReserveFailedReply> payloadType() {
        return InventoryReserveFailedReply.class;
    }

    @Override
    public void handle(InboxMessage<InventoryReserveFailedReply> message) {
        InventoryReserveFailedReply payload = message.payload();
        orchestrator.onInventoryReserveFailed(InventoryReserveFailedInternalEvent.from(payload));
    }
}
