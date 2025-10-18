package msa.bookloan.adapter.out.persistence.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookloan.application.saga.command.SagaCommand;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandOutboxRecorder {
    private static final String AGGREGATE_TYPE = "LoanSaga"; // 이걸 외부변수화 해야하는지 고민

    private final Clock clock;
    private final Snowflake snowflake;
    private final ObjectMapper objectMapper;
    private final OutboxEventRecordRepository recordRepository;
    private final List<OutboxRoutingResolver<?>> resolvers;

    @Transactional
    public boolean save(SagaCommand command) {
        OutboxRouting routing = route(command);
        String payloadJson = toJson(command);

        int affected = recordRepository.upsertOutbox(
                snowflake.nextId(),
                command.commandId(),
                command.type(),
                command.sagaId(),
                AGGREGATE_TYPE,
                null,
                payloadJson,
                routing.getTopic(),
                routing.getPartitionKey(),
                LocalDateTime.now(clock)
        );

        boolean isNew = (affected == 1);
        if (isNew) {
            log.debug("[Outbox] inserted: type={} sagaId={} cmdId={}", command.type(), command.sagaId(), command.commandId());
        } else {
            log.debug("[Outbox] duplicate-skip: type={} sagaId={} cmdId={}", command.type(), command.sagaId(), command.commandId());
        }

        return isNew;
    }

    private OutboxRouting route(SagaCommand command) {

        List<OutboxRoutingResolver<?>> target = resolvers.stream()
                .filter(resolver -> resolver.supports(command))
                .toList();

        if (target.isEmpty()) {
            throw new IllegalStateException("No OutboxRoutingResolver for type: " + command.getClass().getName());
        }
        if (target.size() > 1 ) {
            throw new IllegalStateException("More than one OutboxRoutingResolver for type: " + command.getClass().getName());
        }

        OutboxRouting routing = target.get(0).resolve(command);
        if (routing == null || routing.getTopic() == null || routing.getPartitionKey() == null) {
            throw new IllegalStateException("Resolver returned null routing/topic for " + command.getClass().getName());
        }

        return routing;
    }

    private String toJson(SagaCommand command) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox serialize fail: " + e.getMessage(), e);
        }
        return payload;
    }
}
