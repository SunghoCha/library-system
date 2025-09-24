package msa.bookloan.infra.kafka.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.infra.inbox.recorder.EventRecorder;
import msa.bookloan.infra.kafka.validator.BookCatalogUpdatedExternalEventPayloadValidator;
import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;
import msa.common.exception.FailureCategory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true") // 테스트 의존성때문에 임시
public class BookCatalogProjectionEventListener {

    private final ApplicationEventPublisher eventPublisher;
    private final EventRecorder eventRecorder;

    @Transactional
    @KafkaListener(
            topics = "${kafka.topics.category-changed}",
            groupId = "${kafka.group.bookloan.category-replica}",
            containerFactory = "bookCatalogListenerFactory"
    )
    public void handleBookCatalogUpdate(ConsumerRecord<String, BookCatalogChangedExternalEventPayload> record) {
        log.debug("Received records from topic={} partition(s) starting at offset={}", record.topic(), record.offset());

        BookCatalogChangedExternalEventPayload payload = record.value();
        if (!BookCatalogUpdatedExternalEventPayloadValidator.isValid(payload)) {
            log.info("Invalid event payload detected: eventId={}, skipping",
                    (payload != null ? payload.getEventId() : "null"));
            eventRecorder.saveDeadLetter(record, FailureCategory.VALIDATION_FAIL);
            return;
        }

        boolean isNew = eventRecorder.saveOrBumpEventRecord(record);
        if (isNew) {
            log.info("Publish domain event: eventId={} type={}", payload.getEventId(), payload.getEventType());
            eventPublisher.publishEvent(payload.toEvent());
        }
    }

}
