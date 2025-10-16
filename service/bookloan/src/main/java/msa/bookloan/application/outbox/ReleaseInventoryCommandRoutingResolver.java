package msa.bookloan.application.outbox;

import lombok.RequiredArgsConstructor;
import msa.bookloan.application.saga.command.ReleaseInventoryCommand;
import msa.bookloan.infra.config.properties.KafkaProps;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReleaseInventoryCommandRoutingResolver implements OutboxRoutingResolver<ReleaseInventoryCommand> {

    private final KafkaProps props;
    @Override public Class<ReleaseInventoryCommand> payloadType() {
        return ReleaseInventoryCommand.class;
    }

    @Override public OutboxRouting doResolve(ReleaseInventoryCommand command) {
        return OutboxRouting.builder()
                .topic(props.getTopicInventoryRelease())
                .partitionKey(String.valueOf(command.bookId()))
                .build();
    }
}
