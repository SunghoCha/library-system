    package msa.bookloan.adapter.out.messaging.outbox;

    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
    import msa.common.events.outbox.dto.OutboxRouting;
    import org.springframework.kafka.core.KafkaTemplate;
    import org.springframework.stereotype.Service;

    import java.time.LocalDateTime;

    @Slf4j
    @Service
    @RequiredArgsConstructor
    public class OutboxEventSender {

        private final OutboxRelayProcessor outboxRelayProcessor;
        private final KafkaTemplate<String, String> kafkaTemplate;

        public void send(OutboxEventRecord record) {
            OutboxRouting routing = record.getRouting();
            if (routing == null || routing.getTopic() == null || routing.getTopic().isBlank()) {
                throw new IllegalStateException("Missing topic for eventId=" + record.getEventId());
            }

            String workerId = record.getWorkerId();
            LocalDateTime claimedAt = record.getPickedAt();

            if (workerId == null || claimedAt == null) {
                log.warn("재발행 요청에 펜싱 토큰 없음. 스킵. eventId={}, workerId={}, pickedAt={}",
                        record.getEventId(), workerId, claimedAt);
                return;
            }

            sendAsync(record, workerId, claimedAt);
        }

        private void sendAsync(OutboxEventRecord record,
                               String workerId,
                               LocalDateTime claimedAt) {

            String topic = record.getRouting().getTopic();
            String key = record.getRouting().getPartitionKey();
            Long eventId = record.getEventId();
            log.info("카프카 발행 시도. topic={}, key={}, eventId={}", topic, key, eventId);

            try {
                kafkaTemplate.send(topic, key, record.getPayload())
                        .whenComplete((result, e) -> {
                            if (e == null && result != null && result.getRecordMetadata() != null) {
                                log.info("카프카 ACK topic={}, partition={}, offset={}, eventId={}",
                                        result.getRecordMetadata().topic(),
                                        result.getRecordMetadata().partition(),
                                        result.getRecordMetadata().offset(),
                                        eventId);
                            }
                            outboxRelayProcessor.updateStatusAfterProcessing(
                                    eventId, workerId, claimedAt, e);
                        });
            } catch (Exception e) {
                outboxRelayProcessor.updateStatusAfterProcessing(
                        eventId, workerId, claimedAt, e);
            }

        }

    }
