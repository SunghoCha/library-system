package msa.bookcatalog.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.outbox.recorder.EventRecorder;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookCatalogExternalEventPublisher {

    private final ObjectMapper objectMapper;
    private final EventRecorder eventRecorder;
    private final OutboxEventSender outboxEventSender;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topics.book-catalog-changed}")
    private String topic;

    @Async("EVENT_ASYNC_TASK_EXECUTOR")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(BookCatalogChangedEvent event) {
        String message = toJson(event);
        if (message == null) {
            eventRecorder.recordDeadLetter(event.getEventId(), "JSON serialization failed");
            return;
        }
        outboxEventSender.sendEventWithRetry(event, topic, message);

    }



    public String toJson(BookCatalogChangedEvent event) {
        try {
            return objectMapper.writeValueAsString(BookCatalogChangedExternalEventPayload.of(event));
        } catch (JsonProcessingException e) {
            log.info("JSON serialization failed, eventId={}", event.getEventId(), e);
            return null;
        }
    }

}
