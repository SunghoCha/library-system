package msa.bookloan.infra.kafka.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.infra.inbox.recorder.EventRecorder;
import msa.bookloan.infra.kafka.validator.BookCatalogUpdatedExternalEventPayloadValidator;
import msa.common.events.inbox.dto.AcknowledgeEvent;
import msa.common.events.DlqPublisher;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookCatalogProjectionEventListener {

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final DlqPublisher dlqPublisher;
    private final EventRecorder eventRecorder;

    @Transactional
    @KafkaListener(
            topics = "${kafka.topics.category-changed}",
            groupId = "${kafka.group.bookloan.category-replica}"
    )
    public void handleBookCatalogUpdate(List<ConsumerRecord<String, BookCatalogChangedExternalEventPayload>> records) {
        log.info("Received {} records from topic={} partition(s) starting at offset={}, up to offset={}",
                records.size(),
                records.get(0).topic(),
                records.get(0).offset(),
                records.get(records.size() - 1).offset());

        records.forEach(this::processInboxEventRecord);
    }

    private void processInboxEventRecord(ConsumerRecord<String, BookCatalogChangedExternalEventPayload> record) {
        BookCatalogChangedExternalEventPayload payload = record.value();
        long eventId = getEventId(payload);

        if (shouldSkip(record)) return;

        Optional<String> serialized = serializePayload(record, eventId);
        if (serialized.isEmpty()) return;

        eventRecorder.saveEventRecord(record, InboxEventRecordStatus.NEW, serialized.get());
        eventPublisher.publishEvent(payload.toEvent());
    }

    private boolean shouldSkip(ConsumerRecord<String, BookCatalogChangedExternalEventPayload> record) {
        BookCatalogChangedExternalEventPayload payload = record.value();
        long eventId = getEventId(payload);

        if (!BookCatalogUpdatedExternalEventPayloadValidator.isValid(payload)) {
            log.info("Invalid event payload detected: eventId={}, skipping", payload.getEventId());
            dlqPublisher.publish(record, new IllegalArgumentException("Invalid payload structure"));
            eventRecorder.saveEventRecord(record, InboxEventRecordStatus.DEAD_LETTER, null);
            return true;
        }

        if (eventRecorder.isDuplicateEvent(eventId)) {
            log.debug("Duplicate event, skipping: {}", eventId);
            return true;
        }
        return false;
    }

    private Optional<String> serializePayload(ConsumerRecord<String, BookCatalogChangedExternalEventPayload> record, Long eventId) {
        try {
            return Optional.of(objectMapper.writeValueAsString(record.value()));
        } catch (JsonProcessingException e) {
            log.info("Payload cannot be serialized, skipping eventId={}", eventId, e);
            dlqPublisher.publish(record, e);
            eventRecorder.saveEventRecord(record, InboxEventRecordStatus.DEAD_LETTER, null);
            return Optional.empty();
        }
    }

    private static long getEventId(BookCatalogChangedExternalEventPayload payload) {
        return Long.parseLong(payload.getEventId());
    }


}
