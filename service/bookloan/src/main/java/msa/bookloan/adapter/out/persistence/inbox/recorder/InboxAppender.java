package msa.bookloan.adapter.out.persistence.inbox.recorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.inbox.repository.InboxEventRecordRepository;
import msa.bookloan.application.event.SagaReplyEnvelope;
import msa.common.events.EventTypeV1;
import msa.common.events.MessageEnvelope;
import msa.common.events.bookcatalog.BookCatalogChangedPayload;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
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

    private final InboxEventRecordRepository eventRecordRepository;
    private final ObjectMapper objectMapper;
    private final Snowflake snowflake;

    public void upsertRecord(ConsumerRecord<String, MessageEnvelope> record) {
        MessageEnvelope envelope = record.value();
        if (envelope.payload() == null) {
            throw new IllegalStateException("Inbox serialize fail: payload is null");
        }
        long eventId = toLong(envelope.eventId());
        long aggregateId = toLong(envelope.aggregateId());
        Long aggregateVersion = envelope.aggregateVersion(); // 사가는 null 가능
        String eventType = envelope.eventType();
        String payloadJson = serializePayload(record, envelope);

        int affected = eventRecordRepository.upsertInbox(
                snowflake.nextId(),
                eventId,
                aggregateId,
                aggregateVersion,
                eventType,
                payloadJson,
                record.topic(),
                record.partition(),
                record.offset()
        );

        log.debug("[Inbox] upsert 완료: type={}, aggId={}, eventId={}, affected={}",
                envelope.eventType(), envelope.aggregateId(),
                envelope.eventId(), affected);

    }

    private String serializePayload(ConsumerRecord<String, ?> record, MessageEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope.payload());
        } catch (JsonProcessingException e) {
            log.info("Inbox serialize fail: eventId={} topic={} partition={} offset={} error={}",
                    envelope.eventId(), record.topic(), record.partition(), record.offset(), e.getMessage());
            throw new IllegalStateException("Inbox serialize fail", e);
        }
    }

    @Deprecated
    public boolean upsertEventRecord(
            ConsumerRecord<String, BookCatalogChangedPayload> record) {

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
                record.topic(),
                record.partition(),
                record.offset()
        );
        boolean isNew = (affected == 1);  // MySQL에서 1=INSERT, 2=UPDATE(duplicate -> seen_count++(중복 발생 횟수))
        log.debug("Inbox UPSERT[catalog]: affected={}, eventId={} type={} topic={} partition={} offset={}",
                affected, eventId, eventType, record.topic(), record.partition(), record.offset());

        return isNew;
    }

    @Deprecated
    public boolean upsertSagaRecord(
            ConsumerRecord<String, SagaReplyEnvelope> record) {

        SagaReplyEnvelope payload = record.value();

        long eventId = Long.parseLong(payload.eventId());
        String eventType = EventTypeV1.SAGA_REPLY.name();
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



}
