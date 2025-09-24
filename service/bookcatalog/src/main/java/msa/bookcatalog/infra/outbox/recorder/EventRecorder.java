package msa.bookcatalog.infra.outbox.recorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecord;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecordRepository;
import msa.bookcatalog.service.exception.OutboxEventRecordNotFoundException;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.snowflake.Snowflake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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
    private final BookCatalogOutboxEventRecordRepository eventRecordRepository;
    private final String topic;

    public EventRecorder(
            Snowflake snowflake,
            ObjectMapper objectMapper,
            BookCatalogOutboxEventRecordRepository eventRecordRepository,
            @Value("${kafka.topics.book-catalog-changed}") String topic
    ) {
        this.snowflake = snowflake;
        this.objectMapper = objectMapper;
        this.eventRecordRepository = eventRecordRepository;
        this.topic = topic; // 주입받은 값으로 초기화
    }

    @Transactional
    public void save(BookCatalogChangedEvent event) {
        String payload = serializeToPayload(event);

        OutboxRouting routing = OutboxRouting.builder()
                .topic(topic)
                .partitionKey(String.valueOf(event.getAggregateId())) // 순서 보장 단위
                .build();

        BookCatalogOutboxEventRecord outboxEventRecord = BookCatalogOutboxEventRecord.builder()
                .id(snowflake.nextId())
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .aggregateId(String.valueOf(event.getAggregateId()))
                .aggregateType(event.getAggregateType())
                .payload(payload)
                .occurredAt(LocalDateTime.now())
                .outboxEventRecordStatus(NEW)
                .routing(routing)
                .build();

        eventRecordRepository.save(outboxEventRecord);
        log.debug("OutboxEventRecord saved: eventId=[{}], dbId=[{}]", event.getEventId(), outboxEventRecord.getId());
    }

    @Retryable(
            retryFor = DataAccessException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 200)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsPublished(Long eventId) {
        int updated = eventRecordRepository.updateStatusIfCurrent(
                eventId, OutboxEventRecordStatus.PUBLISHING, OutboxEventRecordStatus.PUBLISHED);
        if (updated == 0) {
            log.debug("[Outbox] 발행 처리 스킵: 이미 처리되었거나 PUBLISHING 상태가 아님 (eventId={})", eventId);
        } else {
            log.info("[Outbox] 발행 완료 (eventId={})", eventId);
        }
    }

    @Retryable(retryFor = DataAccessException.class, maxAttempts = 5, backoff = @Backoff(delay = 200))
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsDeadLetter(Long eventId, String error) {
        int updated = eventRecordRepository.toDeadLetterIfCurrent(
                eventId,
                List.of(OutboxEventRecordStatus.FAILED, OutboxEventRecordStatus.PUBLISHING),
                OutboxEventRecordStatus.DEAD_LETTER,
                error
        );

        if (updated == 0) {
            log.debug("[Outbox] DL 전이 스킵: 현재 상태가 FAILED/PUBLISHING 아님 (eventId={})", eventId);
        } else {
            log.error("[Outbox] 데드레터 전이 (eventId={}, reason={})", eventId, error);
        }
    }

    @Retryable(retryFor = DataAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 200))
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(Long eventId, String error) {
        int updated = eventRecordRepository.failAndIncrementIfCurrent(
                eventId, OutboxEventRecordStatus.PUBLISHING, OutboxEventRecordStatus.FAILED, error);
        if (updated == 0) {
            log.debug("[Outbox] 실패 스킵: 이미 처리되었거나 PUBLISHING 아님 (eventId={})", eventId);
        } else {
            log.warn("[Outbox] 발행 실패 (eventId={}, reason={})", eventId, error);
        }
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
