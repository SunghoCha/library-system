package msa.bookcatalog.adapter.out.persistence.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.snowflake.Snowflake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static msa.common.events.outbox.OutboxEventRecordStatus.NEW;

@Slf4j
@Component
public class EventRecorder {

    private final Snowflake snowflake;
    private final ObjectMapper objectMapper;
    private final OutboxEventRecordRepository eventRecordRepository;
    private final String topic;

    public EventRecorder(
            Snowflake snowflake,
            ObjectMapper objectMapper,
            OutboxEventRecordRepository eventRecordRepository,
            @Value("${app.kafka.topics.catalog-changed-topic}") String topic
    ) {
        this.snowflake = snowflake;
        this.objectMapper = objectMapper;
        this.eventRecordRepository = eventRecordRepository;
        this.topic = topic; // 주입받은 값으로 초기화
    }

    public OutboxEventRecord toRecord(BookCatalogChangedEvent event) {
        String payload = serializeToPayload(event);

        OutboxRouting routing = OutboxRouting.builder()
                .topic(topic)
                .partitionKey(String.valueOf(event.getAggregateId()))
                .build();

        return OutboxEventRecord.builder()
                .id(snowflake.nextId())
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .aggregateId(String.valueOf(event.getAggregateId()))
                .aggregateType(event.getAggregateType())
                .aggregateVersion(event.getAggregateVersion())
                .payload(payload)
                .occurredAt(event.getOccurredAt())
                .outboxEventRecordStatus(NEW)
                .routing(routing)
                .build();
    }

    @Transactional
    public void save(BookCatalogChangedEvent event) {
        OutboxEventRecord outboxEventRecord = toRecord(event);

        eventRecordRepository.save(outboxEventRecord);
        log.debug("OutboxEventRecord saved: eventId=[{}], dbId=[{}]", event.getEventId(), outboxEventRecord.getId());
    }

    @Transactional
    public void saveAll(List<BookCatalogChangedEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        List<OutboxEventRecord> records = events.stream()
                .map(this::toRecord)
                .toList();

        eventRecordRepository.saveAll(records);
        log.debug("OutboxEventRecord {}건 저장 완료.", records.size());
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markPublishedByEventId(Long eventId,
                                      String workerId,
                                      LocalDateTime claimedAt) {
        int updated = eventRecordRepository.markPublishedByEventId(eventId, workerId, claimedAt);

        if (updated == 0) {
            log.info("[Outbox] 발행 처리 스킵: 펜싱 또는 이미 처리됨 (eventId={}, workerId={}, claimedAt={})",
                    eventId, workerId, claimedAt);
        } else {
            log.info("[Outbox] 발행 완료 (eventId={})", eventId);
        }
        return updated;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markFailedByEventId(Long eventId,
                                   String workerId,
                                   LocalDateTime claimedAt,
                                   String reason) {
        int updated = eventRecordRepository.markFailedByEventId(eventId, workerId, claimedAt, reason);

        if (updated == 0) {
            log.info("[Outbox] 실패 처리 스킵: 펜싱 또는 회수됨 (eventId={}, workerId={}, claimedAt={})",
                    eventId, workerId, claimedAt);
        } else {
            log.warn("[Outbox] 발행 실패 (eventId={}, 이유={})", eventId, reason);
        }
        return updated;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markDeadLetter(Long eventId, String error) {
        int updated = eventRecordRepository.markDeadFromFailed(eventId, error);

        if (updated == 0) {
            log.info("[Outbox] 데드레터 전이 스킵: 현재 상태가 FAILED 아님 (eventId={})", eventId);
        } else {
            log.error("[Outbox] 데드레터로 전이 (eventId={}, 이유={})", eventId, error);
        }

        return updated;
    }

    private String serializeToPayload(BookCatalogChangedEvent event) {
        try {
            BookCatalogChangedExternalEventPayload payload = BookCatalogChangedExternalEventPayload.of(event);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload serialize failed: eventId=" + event.getEventId(), e);
        }

    }

}
