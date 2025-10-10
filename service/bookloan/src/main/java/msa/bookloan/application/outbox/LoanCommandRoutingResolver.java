package msa.bookloan.application.outbox;

import lombok.RequiredArgsConstructor;
import msa.bookloan.application.saga.command.CheckMemberCommand;
import msa.bookloan.infra.config.properties.KafkaProps;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanCommandRoutingResolver implements OutboxRoutingResolver<CheckMemberCommand> {

    private final KafkaProps kafkaProps;

    @Override
    public OutboxRouting resolve(CheckMemberCommand event) {
        return OutboxRouting.builder()
                .topic(kafkaProps.getTopicMemberCheck())
                .partitionKey(event.sagaId())
                .build();
    }
}
