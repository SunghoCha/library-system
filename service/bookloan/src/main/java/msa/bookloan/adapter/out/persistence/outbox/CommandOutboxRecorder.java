package msa.bookloan.adapter.out.persistence.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookloan.application.saga.command.SagaCommand;
import msa.common.events.EventType;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CommandOutboxRecorder {
    private static final String AGGREGATE_TYPE = "LoanSaga"; // 이걸 외부변수화 해야하는지 고민

    private final Snowflake snowflake;
    private final ObjectMapper objectMapper;
    private final OutboxEventRecordRepository recordRepository;
    private final List<OutboxRoutingResolver<?>> resolvers;

    @Transactional
    public void save(SagaCommand command) {
        OutboxRouting routing = route(command);
        String payloadJson = toJson(command);
        OutboxEventRecord record = createOutboxRecord(command, payloadJson, routing);

        recordRepository.save(record);
    }

    private OutboxRouting route(SagaCommand command) {
        OutboxRoutingResolver<?> target = null;

        for (OutboxRoutingResolver<?> resolver : this.resolvers) {
            if (resolver.supports(command)) {
                target = resolver; // 리졸버는 1개씩이여서 중복은 없는 상태
            }
        }

        if (target == null) {
            throw new IllegalStateException("No OutboxRoutingResolver for type: " + command.getClass().getName());
        }

        OutboxRouting routing = target.resolve(command);
        if (routing == null || routing.getTopic() == null) {
            throw new IllegalStateException("Resolver returned null routing/topic for " + command.getClass().getName());
        }

        return routing;
    }

    private OutboxEventRecord createOutboxRecord(SagaCommand command, String payloadJson, OutboxRouting routing) {
        OutboxEventRecord record = OutboxEventRecord.builder()
                .id(snowflake.nextId())
                .eventId(command.commandId())                 // 커맨드 추적용 ID(유니크) 생성된 커맨드의 중복발행 방지
                .eventType(EventType.CREATED)                // 내부 표준: 커맨드 적재는 CREATED로 통일
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(command.sagaId())
                .aggregateVersion(0L) // @Version 아님. saga에선 필요없지만 스키마상 넣음
                .payload(payloadJson)
                .occurredAt(LocalDateTime.now())
                .outboxEventRecordStatus(OutboxEventRecordStatus.NEW)
                .routing(routing)
                .build();
        return record;
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
