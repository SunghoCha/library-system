package msa.inventory.adaptor.out.persistence.inbox.recorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.common.events.MessageEnvelope;
import msa.common.snowflake.Snowflake;
import msa.inventory.adaptor.out.persistence.inbox.repository.InboxEventRecordRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InboxAppender {

    private final InboxEventRecordRepository eventRecordRepository;
    private final ObjectMapper objectMapper;
    private final Snowflake snowflake;

    @Transactional
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

    private static long toLong(String s) {
        return Long.parseLong(s.trim());
    }
}
