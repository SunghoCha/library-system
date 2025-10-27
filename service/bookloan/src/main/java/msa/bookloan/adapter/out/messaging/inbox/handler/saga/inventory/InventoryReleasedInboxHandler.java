package msa.bookloan.adapter.out.messaging.inbox.handler.saga.inventory;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.SagaReplyType;
import msa.bookloan.application.saga.reply.inventory.InventoryReleasedReply;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryReleasedInboxHandler implements InboxEventHandler<InventoryReleasedReply> {

    private final LoanRequestSagaOrchestrator orchestrator;

    @Override
    public String eventType() {
        return SagaReplyType.INVENTORY_RELEASED.getValue();
    }

    @Override
    public Class<InventoryReleasedReply> payloadType() {
        return InventoryReleasedReply.class;
    }

    @Override
    public void handle(InventoryReleasedReply payload) {
        orchestrator.onInventoryReleased(payload);
    }
}
