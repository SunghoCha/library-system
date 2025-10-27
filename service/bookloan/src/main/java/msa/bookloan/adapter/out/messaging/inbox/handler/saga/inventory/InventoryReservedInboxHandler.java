package msa.bookloan.adapter.out.messaging.inbox.handler.saga.inventory;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.SagaReplyType;
import msa.bookloan.application.saga.reply.inventory.InventoryReservedReply;
import org.springframework.stereotype.Component;

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
    public void handle(InventoryReservedReply payload) {
        orchestrator.onInventoryReserved(payload);
    }
}
