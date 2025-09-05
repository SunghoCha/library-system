package msa.bookcatalog.infra.outbox.recorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecord;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecordRepository;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.snowflake.Snowflake;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static msa.common.events.outbox.OutboxEventRecordStatus.*;

@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class EventRecorder {

    private final BookCatalogOutboxEventRecordRepository eventRecordRepository;
    private final Snowflake snowflake;

    public void save(BookCatalogChangedEvent event) {
        BookCatalogOutboxEventRecord outboxEventRecord = BookCatalogOutboxEventRecord.builder()
                .id(snowflake.nextId())
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .occurredAt(LocalDateTime.now())
                .outboxEventRecordStatus(NEW)
                .build();
        eventRecordRepository.save(outboxEventRecord);
        log.debug("OutboxEventRecord saved: eventId={}, dbId={}", event.getEventId(), outboxEventRecord.getId());
    }

    @Retryable(
            retryFor = DataAccessException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 200)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long eventId) {
        Long updated = markAsPublished(eventId);
        if (updated == 1) {
            log.info("Published event {}", eventId);
        } else {
            log.debug("Already published or not eligible: {}", eventId);
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


    public void recordDeadLetter(Long eventId, String errorMessage) {
        try {
            markAsDeadLetter(eventId, errorMessage);
        } catch (Exception e) {
            log.warn("DEAD_LETTER marking failed, eventId={}", eventId, e);
        }
    }

    private Long markAsPublished(Long eventId) {
        logStatusUpdated(eventId, PUBLISHED);
        return eventRecordRepository.updateStatus(eventId, PUBLISHED, List.of(NEW, FAILED));
    }

    private Long markAsFailed(Long eventId, String errorMessage) {
        logStatusUpdated(eventId, FAILED);
        return eventRecordRepository.markAsFailed(eventId, errorMessage);
    }

    private Long markAsDeadLetter(Long eventId, String errorMessage) {
        logStatusUpdated(eventId, DEAD_LETTER);
        return eventRecordRepository.markAsDeadLetter(eventId, errorMessage);
    }

    private static void logStatusUpdated(Long eventId, OutboxEventRecordStatus status) {
        log.debug("EventRecordStatus updated [eventId={}, EventRecordStatus={}]",
                eventId, status);
    }
}
