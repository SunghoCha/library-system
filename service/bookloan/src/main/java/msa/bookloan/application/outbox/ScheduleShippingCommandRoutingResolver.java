package msa.bookloan.application.outbox;

import lombok.RequiredArgsConstructor;
import msa.bookloan.application.saga.command.ScheduleShippingCommand;
import msa.bookloan.infra.config.properties.KafkaProps;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduleShippingCommandRoutingResolver implements OutboxRoutingResolver<ScheduleShippingCommand> {

    private final KafkaProps kafkaProps;

    @Override
    public Class<ScheduleShippingCommand> payloadType() {
        return ScheduleShippingCommand.class;
    }

    @Override
    public OutboxRouting doResolve(ScheduleShippingCommand command) {
        return OutboxRouting.builder()
                .topic(kafkaProps.getTopicInventoryReserve())
                .partitionKey(command.sagaId())
                .build();
    }
}
