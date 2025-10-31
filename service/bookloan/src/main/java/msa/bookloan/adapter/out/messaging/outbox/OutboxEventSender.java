package msa.bookloan.adapter.out.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.common.events.MessageEnvelope;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.util.IdConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class OutboxEventSender {

    private final ObjectMapper objectMapper;
    private final OutboxRelayProcessor outboxRelayProcessor;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(OutboxEventRecord record) {
        OutboxRouting routing = record.getRouting();
        if (routing == null || routing.getTopic() == null || routing.getTopic().isBlank()) {
            throw new IllegalStateException("Missing topic for eventId=" + record.getEventId());
        }

        String leaseId = record.getLeaseId();
        if (leaseId == null) {
            log.warn("재발행 요청에 펜싱 토큰 없음. 스킵. eventId={}, leaseId={}", record.getEventId(), leaseId);
            return;
        }

        sendAsync(record, convertToEnvelopeJson(record), leaseId);
    }

    private void sendAsync(OutboxEventRecord record, String envelopeJson, String leaseId) {

        String topic = record.getRouting().getTopic();
        String key = record.getRouting().getPartitionKey();
        Long eventId = record.getEventId();
        log.info("카프카 발행 시도. topic={}, key={}, eventId={}", topic, key, eventId);

        try {
            kafkaTemplate.send(topic, key, envelopeJson)
                    .whenComplete((result, e) -> {
                        if (e == null && result != null && result.getRecordMetadata() != null) {
                            log.info("카프카 ACK topic={}, partition={}, offset={}, eventId={}",
                                    result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset(),
                                    eventId);
                        }
                        outboxRelayProcessor.updateStatusAfterProcessing(
                                eventId, leaseId, e);
                    });
        } catch (Exception e) {
            outboxRelayProcessor.updateStatusAfterProcessing(
                    eventId, leaseId, e);
        }

    }

    private String convertToEnvelopeJson(OutboxEventRecord record) {
        JsonNode payloadNode = toJsonNode(record.getPayload());
        MessageEnvelope envelope = new MessageEnvelope(
                IdConverter.toStringOrNull(record.getEventId()),
                IdConverter.toStringOrNull(record.getAggregateId()),
                record.getAggregateVersion(),
                record.getEventType(),
                payloadNode
        );
        return toJson(envelope);
    }

    private JsonNode toJsonNode(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload is not valid JSON. event cannot be wrapped", e);
        }
    }

    private String toJson(MessageEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize envelope", e);
        }
    }

}
