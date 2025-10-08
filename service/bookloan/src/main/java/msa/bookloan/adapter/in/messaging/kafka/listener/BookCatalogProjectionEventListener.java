package msa.bookloan.adapter.in.messaging.kafka.listener;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.kafka.util.validator.EventPayloadValidator;
import msa.bookloan.adapter.out.persistence.inbox.recorder.InboxAppender;
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
    private final InboxAppender inboxAppender;
    private final EventPayloadValidator payloadValidator;

    @Transactional
    @KafkaListener(
            topics = "${kafka.topics.category-changed}",
            groupId = "${kafka.group.bookloan.category-replica}",
            containerFactory = "bookCatalogListenerFactory"
    )
    public void handleBookCatalogUpdate(ConsumerRecord<String, BookCatalogChangedExternalEventPayload> record) {
        log.debug("카프카 레코드 수신: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        BookCatalogChangedExternalEventPayload payload = record.value();

        if (payload == null) {
            log.info("페이로드가 null 입니다. DLQ로 저장합니다. [topic={}, partition={}, offset={}]",
                    record.topic(), record.partition(), record.offset());
            inboxAppender.saveDeadLetter(record, FailureCategory.VALIDATION_FAIL);
            return;
        }

        try {
            payloadValidator.validateOrThrow(payload);
        } catch (ConstraintViolationException ex) {
            log.info("페이로드 검증 실패: {} [eventId={}, topic={}, partition={}, offset={}]",
                    summarize(ex), payload.getEventId(), record.topic(), record.partition(), record.offset());
            inboxAppender.saveDeadLetter(record, FailureCategory.VALIDATION_FAIL);
            return;
        }

        boolean isNew = inboxAppender.saveOrBumpEventRecord(record);
        if (isNew) {
            log.info("도메인 이벤트 발행: eventId={}, type={}, version={}",
                    payload.getEventId(), payload.getEventType(), payload.getAggregateVersion());
            eventPublisher.publishEvent(payload.toEvent());
        } else {
            log.debug("중복/재처리 스킵: eventId={}, version={}",
                    payload.getEventId(), payload.getAggregateVersion());
        }


    }

    private static String summarize(ConstraintViolationException ex) {
        return ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("violations");
    }

}
