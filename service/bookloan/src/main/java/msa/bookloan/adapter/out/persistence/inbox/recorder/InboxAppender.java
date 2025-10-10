package msa.bookloan.adapter.out.persistence.inbox.recorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.inbox.repository.InboxEventRecordRepository;
import msa.bookloan.application.event.SagaReplyEnvelope;
import msa.common.domain.model.InboxSource;
import msa.common.events.EventType;
import msa.common.events.bookcatalog.BookCatalogChangedPayload;
import msa.common.events.inbox.dto.ConsumerRecordMetadata;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.exception.FailureCategory;
import msa.common.snowflake.Snowflake;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static msa.common.events.inbox.dto.InboxEventRecordStatus.*;


@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class InboxAppender {

    private static final int MAX_ATTEMPTS = 3;

    private final InboxEventRecordRepository eventRecordRepository;
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
            log.info("이벤트 발행 성공 {}", eventId);
        } else {
            log.debug("이미 발행되었거나 유효하지 않음: {}", eventId);
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

    private ConsumerRecordMetadata createConsumerRecordMetadata(ConsumerRecord<String, BookCatalogChangedPayload> record) {
        return ConsumerRecordMetadata.builder()
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .build();
    }

    public boolean upsertEventRecord(
            ConsumerRecord<String, BookCatalogChangedPayload> record, InboxSource source) {

        BookCatalogChangedPayload payload = record.value();
        long eventId = toLong(payload.eventId());
        String eventType = payload.eventType();

        final String serializedPayload;
        try {
            serializedPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.info("Inbox serialize fail: eventId={} topic={} partition={} offset={} error={}",
                    eventId, record.topic(), record.partition(), record.offset(), e.getMessage());
            throw new IllegalStateException("Inbox serialize fail(catalog)", e);
        }

        // 업서트
        int affected = eventRecordRepository.upsertInbox(
                snowflake.nextId(),
                eventId,
                toLong(payload.aggregateId()),
                payload.aggregateVersion(),
                eventType,
                serializedPayload,
                source.name(),
                record.topic(),
                record.partition(),
                record.offset()
        );
        boolean isNew = (affected == 1);  // MySQL에서 1=INSERT, 2=UPDATE(duplicate -> seen_count++(중복 발생 횟수))
        log.debug("Inbox UPSERT[catalog]: affected={}, eventId={} type={} topic={} partition={} offset={}",
                affected, eventId, eventType, record.topic(), record.partition(), record.offset());

        return isNew;
    }

    public boolean upsertSagaRecord(
            ConsumerRecord<String, SagaReplyEnvelope> record, InboxSource source) {

        SagaReplyEnvelope payload = record.value();
        long eventId = Long.parseLong(payload.eventId());
        String eventType = EventType.SAGA_REPLY.name();
        long aggregateId = Long.parseLong(payload.aggregateId());
        Long aggregateVersion = payload.sourceAggregateVersion();

        final String serializedPayload;
        try {
            serializedPayload = objectMapper.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.info("Inbox serialize fail[replies]: sagaId={} topic={} partition={} offset={} error={}",
                    payload.sagaId(), record.topic(), record.partition(), record.offset(), e.getMessage());
            throw new IllegalStateException("Inbox serialize fail(replies)", e);
        }

        int affected = eventRecordRepository.upsertInbox(
                snowflake.nextId(),
                eventId,
                aggregateId,
                aggregateVersion, // aggregateVersion 없음 (사가에서 필요없어보임)
                eventType,
                serializedPayload,
                source.name(),
                record.topic(),
                record.partition(),
                record.offset()
        );
        boolean isNew = (affected == 1);
        log.debug("Inbox UPSERT[replies]: affected={}, eventId={} type={} topic={} partition={} offset={}",
                affected, eventId, eventType, record.topic(), record.partition(), record.offset());

        return isNew;
    }

    private static long toLong(String s) {
        return Long.parseLong(s.trim());
    }

    private static void logStatusUpdate(Long eventId, InboxEventRecordStatus status) {
        log.debug("EventRecordStatus updated [eventId={}, EventRecordStatus={}]",
                eventId, status);
    }

}
