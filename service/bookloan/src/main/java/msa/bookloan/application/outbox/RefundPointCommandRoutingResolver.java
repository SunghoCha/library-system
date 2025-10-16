package msa.bookloan.application.outbox;

import lombok.RequiredArgsConstructor;
import msa.bookloan.application.saga.command.RefundPointCommand;
import msa.bookloan.infra.config.properties.KafkaProps;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundPointCommandRoutingResolver implements OutboxRoutingResolver<RefundPointCommand> {

    private final KafkaProps props;

    @Override public Class<RefundPointCommand> payloadType() {
        return RefundPointCommand.class;
    }

    @Override public OutboxRouting doResolve(RefundPointCommand command) {
        return OutboxRouting.builder()
                .topic(props.getTopicPointRefund())
                .partitionKey(String.valueOf(command.memberId()))
                .build();
    }
}
