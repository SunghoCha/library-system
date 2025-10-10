package msa.bookcatalog.adapter.out.messaging.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.config.properties.OutboxSchedulerProps;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookcatalog.application.service.catalog.exception.OutboxEventRecordNotFoundException;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.snowflake.InstanceIdentity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventSender {

    private final InstanceIdentity identity;
    private final OutboxSchedulerProps props;
    private final OutboxRelayProcessor outboxRelayProcessor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventRecordRepository outboxRepository;
    private final ImmediateClaimer claimer;

    public void send(BookCatalogChangedEvent event) {
        Long eventId = event.getEventId();
        OutboxEventRecord record = outboxRepository.findByEventId(eventId)
                .orElseThrow(OutboxEventRecordNotFoundException::new);

        OutboxRouting routing = record.getRouting();
        if (routing == null) {
            throw new IllegalStateException("OutboxRouting is null for eventId=" + record.getEventId());
        }

        String workerId = identity.workerId();
        LocalDateTime claimedAt = LocalDateTime.now();

        boolean claimed = claimer.tryClaim(eventId, workerId, claimedAt, props.leaseSeconds());
        if (!claimed) {
            log.info("즉시 발행 선점 스킵: 이미 선점되었거나 상태가 NEW가 아님 (eventId={})", eventId);
            return;
        }

        sendAsync(record, workerId, claimedAt);
    }

    public void resend(OutboxEventRecord record) {
        OutboxRouting routing = record.getRouting();
        if (routing == null) {
            throw new IllegalStateException("OutboxRouting is null for eventId=" + record.getEventId());
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
                        outboxRelayProcessor.updateStatusAfterProcessing(
                                eventId, workerId, claimedAt, e);
                    });
        } catch (Exception e) {
            outboxRelayProcessor.updateStatusAfterProcessing(
                    eventId, workerId, claimedAt, e);
        }

    }

}
