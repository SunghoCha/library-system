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
public class SagaReplyKafkaListener {

    private final InboxAppender inboxAppender;
    private final EventPayloadValidator payloadValidator;

    @KafkaListener(
            id = "${app.kafka.listeners.saga-replies.id:sagaRepliesListener}",
            topics = "${app.kafka.topic-saga-replies}",
            groupId = "${app.kafka.group-saga-replies}"
    )
    @Transactional
    public void onSagaReply(ConsumerRecord<String, MessageEnvelope> record) {
        log.debug("카프카 레코드 수신: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        MessageEnvelope envelope = record.value();

        // envelop null 체크
        if (envelope == null) {
            log.warn("[Replies][드롭] envelope=null (topic={}, partition={}, offset={})",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        // 유효성 검증
        try {
            payloadValidator.validateOrThrow(envelope);
        } catch (ConstraintViolationException e) {
            String msg = summarize(e);
            log.warn("[Replies][드롭] 유효성 검증 실패: {} [eventId={}, type={}, topic={}, partition={}, offset={}]",
                    msg, envelope.eventId(), envelope.eventType(), record.topic(), record.partition(), record.offset());
            return;
        }

        // 업서트로 인박스 저장
        try {
            inboxAppender.upsertRecord(record);
        } catch (IllegalStateException e) { // 직렬화,매핑 실패 (재시도 무의미)
            log.warn("[Replies][드롭] 인박스 업서트 실패: {} [eventId={}, type={}, topic={}, partition={}, offset={}]",
                    e.getMessage(), envelope.eventId(), envelope.eventType(),
                    record.topic(), record.partition(), record.offset());
        }

    }

    private static String summarize(ConstraintViolationException ex) {
        return ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("violations");
    }
}
