package msa.inventory.adaptor.in.messaging.inbox.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.common.events.bookloan.saga.command.ReleaseInventoryCommand;
import msa.common.events.bookloan.saga.command.SagaCommandType;
import msa.inventory.adaptor.in.messaging.inbox.InboxMessage;
import msa.inventory.application.service.InventoryService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseInventoryInboxHandler implements InboxEventHandler<ReleaseInventoryCommand> {

    private final InventoryService inventoryService;

    @Override
    public String eventType() {
        return SagaCommandType.INVENTORY_RELEASE.getValue();
    }

    @Override
    public Class<ReleaseInventoryCommand> payloadType() {
        return ReleaseInventoryCommand.class;
    }

    @Override
    public void handle(InboxMessage<ReleaseInventoryCommand> message) {
        ReleaseInventoryCommand payload = message.payload();

        log.info("[Inventory][Release] 수신: sagaId={}, eventId={}, bookId={}",
                payload.sagaId(), message.eventId(), payload.bookId());

        inventoryService.release(payload);
    }
}
