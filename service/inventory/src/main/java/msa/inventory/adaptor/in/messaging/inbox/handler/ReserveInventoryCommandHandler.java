package msa.inventory.adaptor.in.messaging.inbox.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.common.events.bookloan.saga.command.ReserveInventoryCommand;
import msa.common.events.bookloan.saga.command.SagaCommandType;
import msa.inventory.adaptor.in.messaging.inbox.InboxMessage;
import msa.inventory.application.service.InventoryService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReserveInventoryCommandHandler implements InboxEventHandler<ReserveInventoryCommand> {

    private final InventoryService inventoryService;

    @Override
    public String eventType() {
        return SagaCommandType.INVENTORY_RESERVE.getValue();
    }

    @Override
    public Class<ReserveInventoryCommand> payloadType() {
        return ReserveInventoryCommand.class;
    }

    @Override
    public void handle(InboxMessage<ReserveInventoryCommand> message) {
        ReserveInventoryCommand payload = message.payload();

        log.info("[Inventory][Reserve] 수신: sagaId={}, eventId={}, bookId={}",
                payload.sagaId(), message.eventId(), payload.bookId());

        inventoryService.reserve(payload);

    }
}
