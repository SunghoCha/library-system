package msa.bookloan.adapter.out.persistence.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import msa.common.events.EventType;
import msa.common.events.outbox.OutboxRecordableEvent;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.snowflake.Snowflake;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static msa.common.events.outbox.OutboxEventRecordStatus.NEW;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventRecorder {
    private static final String AGGREGATE_TYPE = "BookLoan";

    private final Snowflake snowflake;
    private final ObjectMapper objectMapper;
    private final OutboxEventRecordRepository eventRecordRepository;
    private final OutboxRoutingResolver<LoanRequestedInternalEvent> routingResolver;

    @Transactional
    public void save(OutboxRecordableEvent event) {
        OutboxEventRecord record = toRecord(event);
        try {
            eventRecordRepository.save(record);
            log.debug("[Outbox] saved: eventId={}, dbId={}", event.eventId(), record.getId());
        } catch (DataIntegrityViolationException dup) {
            // event_id 유니크 충돌 : 이미 저장된 이벤트로 간주하고 스킵
            log.info("[Outbox] duplicate skipped: eventId={}", event.eventId());
        }
    }

    private OutboxEventRecord toRecord(OutboxRecordableEvent event) {
        String payload = serializeToPayload(event);

        OutboxRouting routing = routingResolver.doResolve(event);
        if (routing == null || routing.topic() == null || routing.partitionKey() == null) {
            throw new IllegalStateException("Invalid routing for event: " + event);
        }

        return OutboxEventRecord.builder()
                .id(snowflake.nextId())
                .eventId(event.eventId())
                .eventType(EventType.CREATED)                 // 사가 시작이므로 CREATED로 고정
                .aggregateId(String.valueOf(event.loanId()))
                .aggregateType(AGGREGATE_TYPE)
                .aggregateVersion(event.aggregateVersion())
                .payload(payload)
                .occurredAt(event.occurredAt())
                .outboxEventRecordStatus(NEW)
                .routing(routing)
                .build();
    }

    private String serializeToPayload(OutboxRecordableEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload serialize failed: eventId=" + event.eventId(), e);
        }
    }
}
