package msa.bookloan.application.outbox;

import lombok.RequiredArgsConstructor;
import msa.bookloan.application.saga.command.ChargePointCommand;
import msa.bookloan.infra.config.properties.KafkaProps;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChargePointCommandRoutingResolver implements OutboxRoutingResolver<ChargePointCommand> {

    private final KafkaProps kafkaProps;

    @Override
    public Class<ChargePointCommand> payloadType() {
        return ChargePointCommand.class;
    }

    @Override
    public OutboxRouting doResolve(ChargePointCommand command) {
        return OutboxRouting.builder()
                .topic(kafkaProps.getTopicPointCharge())
                .partitionKey(String.valueOf(command.memberId()))
                .build();
    }
}
