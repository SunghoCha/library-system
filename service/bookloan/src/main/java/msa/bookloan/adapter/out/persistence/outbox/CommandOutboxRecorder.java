package msa.bookloan.adapter.out.persistence.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookloan.application.saga.command.CheckMemberCommand;
import msa.bookloan.application.saga.command.ReserveInventoryCommand;
import msa.common.events.EventType;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CommandOutboxRecorder {
    private static final String AGGREGATE_TYPE = "LoanSaga";

    private final Snowflake snowflake;
    private final ObjectMapper objectMapper;
    private final OutboxEventRecordRepository recordRepository;
    private final OutboxRoutingResolver<CheckMemberCommand> memberCheckResolver;
    private final OutboxRoutingResolver<ReserveInventoryCommand> inventoryReserveResolver;

    @Transactional
    public void save(CheckMemberCommand cmd) {
        OutboxRouting routing = memberCheckResolver.resolve(cmd);
        saveInternal(cmd.sagaId(), 0L, cmd, routing);
    }

    @Transactional
    public void save(ReserveInventoryCommand cmd) {
        OutboxRouting routing = inventoryReserveResolver.resolve(cmd);
        saveInternal(cmd.sagaId(), 0L, cmd, routing);
    }

    private void saveInternal(String aggregateId, long aggregateVersion, Object payloadObj, OutboxRouting routing) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(payloadObj);
        } catch (Exception e) {
            throw new IllegalStateException("serialize fail", e);
        }

        OutboxEventRecord record = OutboxEventRecord.builder()
                .id(snowflake.nextId())
                .eventId(snowflake.nextId())                 // 커맨드 추적용 ID(유니크)
                .eventType(EventType.CREATED)                // 내부 표준: 커맨드 적재는 CREATED로 통일
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(aggregateId)
                .aggregateVersion(aggregateVersion) // @Version 아님
                .payload(payload)
                .occurredAt(LocalDateTime.now())
                .outboxEventRecordStatus(OutboxEventRecordStatus.NEW)
                .routing(routing)
                .build();

        recordRepository.save(record);
    }
}
