package msa.bookcatalog.infra.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecord;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecordRepository;
import msa.bookcatalog.infra.outbox.scheduler.OutboxEventProcessor;
import msa.bookcatalog.service.exception.OutboxEventRecordNotFoundException;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.events.outbox.dto.OutboxRouting;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventProcessor outboxEventProcessor;
    private final BookCatalogOutboxEventRecordRepository outboxRepository;

    public void send(BookCatalogChangedEvent event) {
        Long eventId = event.getEventId();
        BookCatalogOutboxEventRecord record = outboxRepository.findByEventId(eventId)
                .orElseThrow(OutboxEventRecordNotFoundException::new);

        OutboxRouting routing = record.getRouting();
        if (routing == null) {
            throw new IllegalStateException("OutboxRouting is null for eventId=" + record.getEventId());
        }
        sendAsync(routing.getTopic(), routing.getPartitionKey(), record.getPayload(), eventId);
    }

    public void resend(BookCatalogOutboxEventRecord record) {
        OutboxRouting routing = record.getRouting();
        if (routing == null) {
            throw new IllegalStateException("OutboxRouting is null for eventId=" + record.getEventId());
        }
        String topic  = routing.getTopic();
        String key    = routing.getPartitionKey();
        String value  = record.getPayload();
        Long eventId  = record.getEventId();

        sendAsync(topic, key, value, eventId);
    }

    private void sendAsync(String topic, String key, String payload, Long eventId) {
        log.info("카프카 발행 시도. topic={}, key={}, eventId={}", topic, key, eventId);
        try {
            kafkaTemplate.send(topic, key, payload)
                    .whenComplete((result, ex) -> {outboxEventProcessor.updateStatusAfterProcessing(eventId, ex);});
        } catch (Exception e) {
            outboxEventProcessor.updateStatusAfterProcessing(eventId, e);
        }
    }

}
