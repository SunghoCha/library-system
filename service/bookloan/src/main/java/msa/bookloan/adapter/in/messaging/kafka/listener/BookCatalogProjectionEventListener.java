package msa.bookloan.adapter.in.messaging.kafka.listener;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.kafka.util.CatalogPayloadTranslator;
import msa.bookloan.adapter.in.messaging.kafka.util.InboxSourceResolver;
import msa.bookloan.adapter.in.messaging.kafka.util.validator.EventPayloadValidator;
import msa.bookloan.adapter.out.persistence.inbox.recorder.DeadLetterAppender;
import msa.bookloan.adapter.out.persistence.inbox.recorder.InboxAppender;
import msa.common.domain.model.InboxSource;
import msa.common.events.bookcatalog.BookCatalogChangedPayload;
import msa.common.exception.FailureCategory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
//@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true") // 테스트 의존성때문에 임시
public class BookCatalogProjectionEventListener {

    private final ApplicationEventPublisher eventPublisher;
    private final InboxAppender inboxAppender;
    private final DeadLetterAppender deadLetterAppender;
    private final EventPayloadValidator payloadValidator;
    private final InboxSourceResolver inboxSourceResolver;
    private final CatalogPayloadTranslator payloadTranslator;

    @Transactional
    @KafkaListener(
            topics = "${kafka.topics.category-changed}",
            groupId = "${kafka.group.bookloan.category-replica}",
            containerFactory = "bookCatalogListenerFactory"
    )
    public void handleBookCatalogUpdate(ConsumerRecord<String, BookCatalogChangedPayload> record) {
        log.debug("카프카 레코드 수신: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        InboxSource source = inboxSourceResolver.resolveFromTopic(record.topic());
        BookCatalogChangedPayload payload = record.value();

        // 페이로드 null 체크
        if (payload == null) {
            log.warn("[Catalog][드롭] 페이로드가 null 입니다. source={}, topic={}, partition={}, offset={}",
                    source, record.topic(), record.partition(), record.offset());
            return;
        }

        // 유효성 검사
        try {
            payloadValidator.validateOrThrow(payload);
        } catch (ConstraintViolationException e) {
            String msg = summarize(e);
            log.warn("[Catalog][드롭] 유효성 검증 실패: {} [eventId={}, source={}, topic={}, partition={}, offset={}]",
                    msg, payload.eventId(), source, record.topic(), record.partition(), record.offset());
            return;
        }

        // 업서트
        boolean isNew;
        try {
            isNew = inboxAppender.upsertRecord(record, source);
        } catch (IllegalStateException e) { // 직렬화,매핑 실패 (재시도 무의미)
            log.warn("[Catalog][드롭] 인박스 업서트 실패: {} [eventId={}, source={}, topic={}, partition={}, offset={}]",
                    e.getMessage(), payload.eventId(), source, record.topic(), record.partition(), record.offset());
            return;
        }

        // 새로운 메시지면 이벤트 발행
        if (isNew) {
            log.info("도메인 이벤트 발행: eventId={}, type={}, version={}",
                    payload.eventId(), payload.eventType(), payload.aggregateVersion());
            eventPublisher.publishEvent(payloadTranslator.toInternal(payload));
        } else {
            log.debug("중복/재처리 스킵: eventId={}, version={}",
                    payload.eventId(), payload.aggregateVersion());
        }

    }

    // 예외 메시지 생성용
    private static String summarize(ConstraintViolationException ex) {
        return ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("violations");
    }

}
