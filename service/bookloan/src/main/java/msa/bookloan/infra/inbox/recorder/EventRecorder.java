package msa.bookloan.infra.inbox.recorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.infra.inbox.repository.BookCatalogProjectionInboxEventRecord;
import msa.bookloan.infra.inbox.repository.BookCatalogProjectionEventRecordRepository;
import msa.bookloan.service.exception.InboxEventRecordNotFoundException;
import msa.common.events.inbox.dto.ConsumerRecordMetadata;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;
import msa.common.snowflake.Snowflake;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static msa.common.events.inbox.dto.InboxEventRecordStatus.*;


@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class EventRecorder {

    private static final int MAX_ATTEMPTS = 3;
    private final BookCatalogProjectionEventRecordRepository eventRecordRepository;
    private final Snowflake snowflake;

    public void saveEventRecord(ConsumerRecord<String, BookCatalogChangedExternalEventPayload> record, InboxEventRecordStatus status, String json) {
        ConsumerRecordMetadata recordMetadata = createConsumerRecordMetadata(record);

        BookCatalogProjectionInboxEventRecord eventRecord = BookCatalogProjectionInboxEventRecord.builder()
                .id(snowflake.nextId())
                .eventId(getEventId(record.value()))
                .eventType(record.value().getEventType())
                .payload(json)
                .receivedAt(LocalDateTime.now())
                .inboxEventRecordStatus(status)
                .consumerRecordMetadata(recordMetadata)
                .build();

        eventRecordRepository.save(eventRecord);
    }

    @Transactional
    public void recordSuccess(Long eventId) {
        Long updated = markAsProcessed(eventId);
        if (updated == 1) {
            log.info("Published event {}", eventId);
        } else {
            log.debug("Already published or not eligible: {}", eventId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long eventId, String errorMessage) {
        try {
            Long incremented = eventRecordRepository.incrementRetryCountIfBelowMax(eventId, MAX_ATTEMPTS, errorMessage);
            if (incremented == 0) {
                markAsDeadLetter(eventId);
            } else {
                markAsFailed(eventId);
            }
        } catch (Exception e) {
            log.warn("Failed to recordFailure for {}: {}", eventId, e.getMessage(), e);
        }
    }

    private Long markAsProcessed(Long eventId) {
        logStatusUpdate(eventId, PROCESSED);
        return eventRecordRepository.updateStatusIfPending(eventId, PROCESSED, List.of(NEW, FAILED));
    }

    private Long markAsFailed(Long eventId) {
        logStatusUpdate(eventId, FAILED);
        return eventRecordRepository.updateStatusIfPending(eventId, FAILED, List.of(NEW, FAILED));
    }

    private Long markAsDeadLetter(Long eventId) {
        logStatusUpdate(eventId, DEAD_LETTER);
        return eventRecordRepository.updateStatusIfPending(eventId, DEAD_LETTER, List.of(NEW, FAILED));
    }

    public boolean isDuplicateEvent(long eventId) {
        return eventRecordRepository.existsByEventId(eventId);
    }

    private ConsumerRecordMetadata createConsumerRecordMetadata(ConsumerRecord<String, BookCatalogChangedExternalEventPayload> record) {
        return ConsumerRecordMetadata.builder()
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .build();
    }

    private long getEventId(BookCatalogChangedExternalEventPayload payload) {
        return Long.parseLong(payload.getEventId());
    }


    private static void logStatusUpdate(Long eventId, InboxEventRecordStatus status) {
        log.debug("EventRecordStatus updated [eventId={}, EventRecordStatus={}]",
                eventId, status);
    }


}
