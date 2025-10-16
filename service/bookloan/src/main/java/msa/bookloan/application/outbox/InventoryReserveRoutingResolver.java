package msa.bookloan.application.outbox;

import lombok.RequiredArgsConstructor;
import msa.bookloan.application.saga.command.ReserveInventoryCommand;
import msa.bookloan.infra.config.properties.KafkaProps;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryReserveRoutingResolver implements OutboxRoutingResolver<ReserveInventoryCommand> {

    private final KafkaProps kafkaProps;

    @Override
    public Class<ReserveInventoryCommand> payloadType() {
        return ReserveInventoryCommand.class;
    }

    @Override
    public OutboxRouting doResolve(ReserveInventoryCommand command) {
        return OutboxRouting.builder()
                .topic(kafkaProps.getTopicInventoryReserve())
                .partitionKey(String.valueOf(command.bookId()))
                .build();
    }
}
