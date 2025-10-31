package msa.bookloan.adapter.in.messaging.inbox.handler.saga.inventory;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.inventory.InventoryReleasedInternalEvent;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReleasedReply;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    public void handle(InboxMessage<InventoryReleasedReply> message) {
        InventoryReleasedReply payload = message.payload();
        orchestrator.onInventoryReleased(InventoryReleasedInternalEvent.from(payload));
    }
}
