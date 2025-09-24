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
@RequiredArgsConstructor
public class EventRecorder {

    private final Snowflake snowflake;
    private final ObjectMapper objectMapper;
    private final BookCatalogOutboxEventRecordRepository eventRecordRepository;

    @Value("${kafka.topics.book-catalog-changed}")
    private String topic;

    @Value("${outbox.retry.max-attempts:5}")
    private int maxAttempts;

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
                .payload(payload)
                .occurredAt(LocalDateTime.now())
                .outboxEventRecordStatus(NEW)
                .routing(routing)
                .build();

        eventRecordRepository.save(outboxEventRecord);
        log.debug("OutboxEventRecord saved: eventId=[{}], dbId=[{}]", event.getEventId(), outboxEventRecord.getId());
    }

//    @Retryable(
//            retryFor = DataAccessException.class,
//            maxAttempts = 5,
//            backoff = @Backoff(delay = 200)
//    )
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void recordSuccess(Long eventId) {
//        Long updated = markAsPublished(eventId);
//        if (updated == 1) {
//            log.info("Published event {}", eventId);
//        } else {
//            log.debug("Already published or not eligible: {}", eventId);
//        }
//    }

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

//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void markAsDeadLetter(Long eventId, String errorMessage) {
//        try {
//            BookCatalogOutboxEventRecord record = findByEventId(eventId);
//            record.markAsDeadLetter(errorMessage);
//            log.error("Event [{}] marked as DEAD_LETTER directly. Reason: {}", eventId, errorMessage);
//        } catch (Exception e) {
//            log.error("Critical error during marking event [{}] as dead letter. Manual check required.", eventId, e);
//        }
//    }

    @Retryable(retryFor = DataAccessException.class, maxAttempts = 5, backoff = @Backoff(delay = 200))
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsDeadLetter(Long eventId, String error) {
        // DEAD_LETTER는 보통 FAILED에서만 가는 게 자연스럽지만,
        // 운영 상황에 따라 PUBLISHING에서도 바로 보내고 싶으면 둘 다 허용.
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

//    @Retryable(
//            retryFor = DataAccessException.class,
//            maxAttempts = 5,
//            backoff = @Backoff(delay = 100)
//    )
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void handleFailure(Long eventId, String errorMessage) {
//        BookCatalogOutboxEventRecord record = findByEventId(eventId);
//        record.handleFailure(errorMessage, maxAttempts);
//        log.info("Failure handled for event [{}]. New status: {}, RetryCount: {}",
//                eventId, record.getOutboxEventRecordStatus(), record.getRetryCount());
//    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(Long eventId, String error) {
        int updated = eventRecordRepository.failAndIncrementIfCurrent(
                eventId, OutboxEventRecordStatus.PUBLISHING, OutboxEventRecordStatus.FAILED, error);
        if (updated == 0) {
            log.debug("[Outbox] 실패 스킵: 이미 처리되었거나 PUBLISHING 아님 (eventId={})", eventId);
        } else {
            log.warn("[Outbox] 발행 실패 (eventId={}, reason={})", eventId, error);
            // 여기서 임계치 넘으면 DEAD_LETTER 전이도 별도 가드 UPDATE로
        }
    }

//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void recordFailure(Long eventId, String errorMessage) {
//        try {
//            Long incremented = eventRecordRepository.incrementRetryCountIfBelowMax(eventId, MAX_ATTEMPTS, errorMessage);
//            if (incremented == 0) {
//                markAsDeadLetter(eventId);
//            } else {
//                markAsFailed(eventId);
//            }
//        } catch (Exception e) {
//            log.warn("Failed to recordFailure for {}: {}", eventId, e.getMessage(), e);
//        }
//    }


    private BookCatalogOutboxEventRecord findByEventId(Long eventId) {
        // findByEventId가 있다고 가정, 없으면 findById 사용
        return eventRecordRepository.findByEventId(eventId)
                .orElseThrow(OutboxEventRecordNotFoundException::new);
    }

    private String serializeToPayload(BookCatalogChangedEvent event) {
        try {
            BookCatalogChangedExternalEventPayload payload = BookCatalogChangedExternalEventPayload.of(event);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload serialize failed: eventId=" + event.getEventId(), e);
        }
    }

    private static void logStatusUpdated(Long eventId, OutboxEventRecordStatus status) {
        log.debug("EventRecordStatus updated [eventId={}, EventRecordStatus={}]",
                eventId, status);
    }
}
