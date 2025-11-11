package msa.bookloan.adapter.in.messaging.kafka.listener;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.kafka.util.validator.EventPayloadValidator;
import msa.bookloan.adapter.out.persistence.inbox.recorder.InboxAppender;
import msa.common.events.MessageEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class BookCatalogProjectionEventListener {

    private final InboxAppender inboxAppender;
    private final EventPayloadValidator payloadValidator;

    @Transactional
    @KafkaListener(
            id = "${app.kafka.listeners.catalog-replica.id:catalogReplicaListener}",
            topics = "${app.kafka.topic-catalog-changed}",
            groupId = "${app.kafka.group-catalog-replica}"
    )
    public void handleBookCatalogUpdate(ConsumerRecord<String, MessageEnvelope> record) {
        log.debug("카프카 레코드 수신: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        MessageEnvelope payload = record.value();

        // 페이로드 null 체크
        if (payload == null) {
            log.warn("[Catalog][드롭] 페이로드가 null 입니다. topic={}, partition={}, offset={}",
                     record.topic(), record.partition(), record.offset());
            return;
        }

        // 유효성 검사
        try {
            payloadValidator.validateOrThrow(payload);
        } catch (ConstraintViolationException e) {
            String msg = summarize(e); // 일단 기록 안하고 로그만
            log.warn("[Catalog][드롭] 유효성 검증 실패: {} [eventId={}, topic={}, partition={}, offset={}]",
                    msg, payload.eventId(), record.topic(), record.partition(), record.offset());
            return;
        }

        // 업서트
        try {
            inboxAppender.upsertRecord(record);
        } catch (IllegalStateException e) { // 직렬화,매핑 실패 (재시도 무의미)
            log.warn("[Catalog][드롭] 인박스 업서트 실패: {} [eventId={},topic={}, partition={}, offset={}]",
                    e.getMessage(), payload.eventId(), record.topic(), record.partition(), record.offset());
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
