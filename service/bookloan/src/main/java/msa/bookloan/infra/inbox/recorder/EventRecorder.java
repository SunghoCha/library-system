package msa.bookloan.infra.inbox.recorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.infra.inbox.repository.BookCatalogProjectionInboxEventRecord;
import msa.bookloan.infra.inbox.repository.BookCatalogProjectionEventRecordRepository;
import msa.common.events.inbox.dto.ConsumerRecordMetadata;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;
import msa.common.exception.FailureCategory;
import msa.common.snowflake.Snowflake;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static msa.common.events.inbox.dto.InboxEventRecordStatus.*;


@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class EventRecorder {

    private static final int MAX_ATTEMPTS = 3;

    private final BookCatalogProjectionEventRecordRepository eventRecordRepository;
    private final ObjectMapper objectMapper;
    private final Snowflake snowflake;

//    public void saveEventRecord(ConsumerRecord<String, BookCatalogChangedExternalEventPayload> record, InboxEventRecordStatus status, String json) {
//        ConsumerRecordMetadata recordMetadata = createConsumerRecordMetadata(record);
//
//        BookCatalogProjectionInboxEventRecord eventRecord = BookCatalogProjectionInboxEventRecord.builder()
//                .id(snowflake.nextId())
//                .eventId(getEventId(record.value()))
//                .eventType(record.value().getEventType())
//                .payload(json)
//                .inboxEventRecordStatus(status)
//                .consumerRecordMetadata(recordMetadata)
//                .build();
//
//        eventRecordRepository.save(eventRecord);
//    }

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


    public boolean saveOrBumpEventRecord(ConsumerRecord<String, BookCatalogChangedExternalEventPayload> record) {
        BookCatalogChangedExternalEventPayload payload = record.value();
        long eventId = toLong(payload.getEventId());
        String eventType = payload.getEventType().name();

        final String serializedPayload;
        try {
            serializedPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.info("Inbox serialize fail: eventId={} topic={} partition={} offset={} error={}",
                    eventId, record.topic(), record.partition(), record.offset(), e.getMessage());
            saveDeadLetter(record, FailureCategory.SERIALIZE_FAIL);
            return false;
        }

        int affected = eventRecordRepository.upsertInbox(
                snowflake.nextId(),
                eventId,
                toLong(payload.getAggregateId()),
                toLong(payload.getAggregateVersion()),
                eventType,
                serializedPayload,
                record.topic(),
                record.partition(),
                record.offset()
        );
        boolean isNew = (affected == 1);  // MySQL: 1=INSERT, 2=UPDATE(duplicate -> seen_count++(중복 발생 횟수))

        logInsertOrDuplicated(record, isNew, eventId, eventType);
        return isNew;
    }

    private static void logInsertOrDuplicated(ConsumerRecord<String, BookCatalogChangedExternalEventPayload> record, boolean isNew, long eventId, String eventType) {
        if (isNew) {
            log.debug("Inbox INSERT: eventId={} type={} topic={} partition={} offset={}",
                    eventId, eventType, record.topic(), record.partition(), record.offset());
        } else {
            log.info("Inbox DUPLICATED: eventId={} type={} topic={} partition={} offset={} ",
                    eventId, eventType, record.topic(), record.partition(), record.offset());
        }
    }

    public void saveDeadLetter(ConsumerRecord<String, BookCatalogChangedExternalEventPayload> record, FailureCategory failureCategory) {
        BookCatalogChangedExternalEventPayload payload = record.value();

        long eventId = -1L;
        if (payload != null) {
            try {
                eventId = Long.parseLong(payload.getEventId());
            } catch (NumberFormatException e) {
                // 예외 던지지않고 그대로 삼킴
            }
        }
        if (eventId <= 0L) {
            eventId = syntheticEventId(record);
        }

        String eventType = (payload != null && payload.getEventType() != null)
                ? payload.getEventType().name()
                : null;

        log.info("DeadLetter: category={} eventId={} topic={} partition={} offset={}",
                failureCategory.name(),
                (payload != null ? payload.getEventId() : "null"),
                record.topic(), record.partition(), record.offset());

        eventRecordRepository.upsertDeadLetter(
                snowflake.nextId(),
                eventId,
                Long.parseLong(payload.getAggregateId()),
                Long.parseLong(payload.getAggregateVersion()),
                eventType,
                record.topic(),
                record.partition(),
                record.offset(),
                null,
                failureCategory.name()
        );
    }

    private static long toLong(String s) {
        return Long.parseLong(s.trim());
    }

    // record의 topic, partition, offset 정보 기반으로 이벤트 아이디 생성하는 해시함수
    // 이벤트ID가 NULL인 경우 항상 같은 아이디를 만들어서 DLT도 중복체크가 가능하도록 함
    // 이런 방식으로 진짜로 해야할지 확신 못하겠음
    private long syntheticEventId(ConsumerRecord<?, ?> record) {
        long h = 1469598103934665603L; // FNV-1a base
        h ^= record.topic().hashCode();
        h *= 1099511628211L;
        h ^= record.partition();
        h *= 1099511628211L;
        long off = record.offset();
        h ^= (off ^ (off >>> 32));
        h *= 1099511628211L;
        return (h == Long.MIN_VALUE) ? 0L : Math.abs(h);
    }

    private static void logStatusUpdate(Long eventId, InboxEventRecordStatus status) {
        log.debug("EventRecordStatus updated [eventId={}, EventRecordStatus={}]",
                eventId, status);
    }

}
