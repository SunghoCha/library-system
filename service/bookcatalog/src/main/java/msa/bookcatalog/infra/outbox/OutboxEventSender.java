package msa.bookcatalog.infra.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.outbox.recorder.EventRecorder;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecord;
import msa.bookcatalog.infra.outbox.scheduler.OutboxEventProcessor;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventProcessor outboxEventProcessor;
    private final EventRecorder eventRecorder;

    public void sendEventWithRetry(BookCatalogChangedEvent event, String topic, String msg) {
        sendToKafka(event, topic, msg);
        eventRecorder.recordSuccess(event.getEventId());
    }

    @Retryable(
            retryFor = {KafkaException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 500))
    private void sendToKafka(BookCatalogChangedEvent event, String topic, String msg) {
        try {
            kafkaTemplate.send(topic, event.getAggregateId(), msg).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new KafkaException("Kafka send failed", e);
        }
    }

    public void resendEvent(BookCatalogOutboxEventRecord record, String topic) {
        kafkaTemplate.send(topic, record.getAggregateId(), record.getPayload())
                .whenComplete((result, ex) -> outboxEventProcessor.updateRecord(record.getEventId(), ex));
    }

    @Recover
    public void recover(Long eventId, KafkaException e) {
        eventRecorder.recordDeadLetter(eventId, e.getMessage());
    }


}
