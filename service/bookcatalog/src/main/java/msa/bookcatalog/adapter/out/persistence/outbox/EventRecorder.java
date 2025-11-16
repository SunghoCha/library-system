package msa.bookcatalog.adapter.out.persistence.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.common.events.outbox.OutboxRecordableEvent;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.time.LocalDateTime.now;
import static msa.common.events.outbox.OutboxEventRecordStatus.NEW;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventRecorder {
    private static final String AGGREGATE_TYPE = "BookCatalog";

    private final Clock clock;
    private final Snowflake snowflake;
    private final ObjectMapper objectMapper;
    private final OutboxEventRecordRepository eventRecordRepository;
    private final List<OutboxRoutingResolver<?>> resolvers;

    @Transactional
    public void save(OutboxRecordableEvent event) {
        String payloadJson = serializeToPayload(event);

        OutboxRouting routing = route(event);
        if (routing == null || routing.getTopic() == null || routing.getPartitionKey() == null) {
            throw new IllegalStateException("Invalid routing for event: " + event);
        }

        int affected = eventRecordRepository.upsertOutbox(
                snowflake.nextId(),
                event.eventId(),
                event.eventType().getValue(),
                event.aggregateId(),
                AGGREGATE_TYPE,
                event.aggregateVersion(),
                payloadJson,
                routing.getTopic(),
                routing.getPartitionKey(),
                LocalDateTime.now(clock)
        );

        log.debug("[Outbox] upsert 완료: type={} aggType={} aggId={} eventId={} affected={}",
                event.eventType().getValue(), AGGREGATE_TYPE, event.aggregateId(),
                event.eventId(), affected);

    }

    @Transactional
    public void saveAll(List<OutboxRecordableEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        Set<Long> eventIdsToSave = events.stream()
                .map(OutboxRecordableEvent::eventId)
                .collect(Collectors.toSet());

        Set<Long> existingEventIds = eventRecordRepository.findExistingEventIdsByEventIdIn(eventIdsToSave);

        List<OutboxEventRecord> newRecords = events.stream()
                .filter(event -> !existingEventIds.contains(event.eventId()))
                .collect(Collectors.toSet()) // eventId로 이퀄스해시코드 구현해서 중복 제거
                .stream()
                .map(this::toRecord)
                .toList();

        if (newRecords.isEmpty()) {
            log.info("[Outbox] 저장할 새로운 이벤트가 없습니다. (전체 {}건 중 중복 {}건)", events.size(), existingEventIds.size());
            return;
        }

        eventRecordRepository.saveAll(newRecords); // 현재 단일 리더 스케줄러 저장방식이라 동시성 문제는 없을듯
        log.debug("OutboxEventRecord {}건 저장 완료. (중복 {}건 스킵)", newRecords.size(), existingEventIds.size());
    }

    public OutboxEventRecord toRecord(OutboxRecordableEvent event) {
        String payload = serializeToPayload(event);

        OutboxRouting routing = route(event);
        if (routing == null || routing.getTopic() == null || routing.getPartitionKey() == null) {
            throw new IllegalStateException("Routing is invalid: " + event);
        }

        return OutboxEventRecord.builder()
                .id(snowflake.nextId())
                .eventId(event.eventId())
                .eventType(event.eventType().getValue())
                .aggregateId(event.aggregateId())
                .aggregateType(event.aggregateType())
                .aggregateVersion(event.aggregateVersion())
                .payload(payload)
                .occurredAt(event.occurredAt())
                .outboxEventRecordStatus(NEW)
                .routing(routing)
                .build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long markPublishedByEventId(Long eventId, String leaseId) {
        long updated = eventRecordRepository.markPublishedByEventId(eventId, leaseId, now(clock));

        if (updated == 0) {
            log.info("[Outbox] 발행 처리 스킵(펜싱/이미 처리): eventId={}, leaseId={}",
                    eventId, leaseId);
        } else {
            log.info("[Outbox] 발행 완료 (eventId={})", eventId);
        }
        return updated;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long markFailedByEventId(Long eventId, String leaseId, String reason) {
        long updated = eventRecordRepository.markFailedByEventId(eventId, leaseId, reason, now(clock));

        if (updated == 0) {
            log.info("[Outbox] 실패 처리 스킵(펜싱/회수됨): eventId={}, leaseId={}",
                    eventId, leaseId);
        } else {
            log.warn("[Outbox] 발행 실패 (eventId={}, 이유={})", eventId, reason);
        }
        return updated;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long markDeadLetter(Long eventId, String error) {
        long updated = eventRecordRepository.markDeadFromFailed(eventId, error, now(clock));

        if (updated == 0) {
            log.info("[Outbox] 데드레터 전이 스킵: 현재 상태가 FAILED 아님 (eventId={})", eventId);
        } else {
            log.error("[Outbox] 데드레터로 전이 (eventId={}, 이유={})", eventId, error);
        }

        return updated;
    }

    private OutboxRouting route(OutboxRecordableEvent event) {
        OutboxRoutingResolver<?> target = null;

        for (OutboxRoutingResolver<?> resolver : resolvers) {
            if (resolver.supports(event)) {
                target = resolver;
            }
        }

        if (target == null) {
            throw new IllegalStateException("No OutboxRoutingResolver for type: " + event.getClass().getName());
        }

        OutboxRouting routing = target.resolve(event);
        if (routing == null || routing.getTopic() == null) {
            throw new IllegalStateException("Resolver returned null routing/topic for " + event.getClass().getName());
        }

        return routing;
    }

    private String serializeToPayload(OutboxRecordableEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload serialize failed: eventId=" + event.eventId(), e);
        }
    }

}
