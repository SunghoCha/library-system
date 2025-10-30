package msa.bookloan.adapter.in.messaging.kafka.listener;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.kafka.util.InboxSourceResolver;
import msa.bookloan.adapter.in.messaging.kafka.util.validator.EventPayloadValidator;
import msa.bookloan.adapter.out.persistence.inbox.recorder.InboxAppender;
import msa.common.domain.model.InboxSource;
import msa.common.events.MessageEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class SagaReplyKafkaListener {

    private final InboxAppender inboxAppender;
    private final InboxSourceResolver inboxSourceResolver;
    private final EventPayloadValidator payloadValidator;

    @KafkaListener(
            topics = "${app.kafka.topic-saga-replies}",
            groupId = "${app.kafka.group-saga-replies}",
            containerFactory = "envelopeListenerFactory"
    )
    @Transactional
    public void onSagaReply(ConsumerRecord<String, MessageEnvelope> record) {
        log.debug("카프카 레코드 수신: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        InboxSource source = inboxSourceResolver.resolveFromTopic(record.topic());
        MessageEnvelope envelope = record.value();

        // envelop null 체크
        if (envelope == null) {
            log.warn("[Replies][드롭] envelope=null (source={}, topic={}, partition={}, offset={})",
                    source, record.topic(), record.partition(), record.offset());
            return;
        }

        // 유효성 검증
        try {
            payloadValidator.validateOrThrow(envelope);
        } catch (ConstraintViolationException e) {
            String msg = summarize(e);
            log.warn("[Replies][드롭] 유효성 검증 실패: {} [eventId={}, type={}, source={}, topic={}, partition={}, offset={}]",
                    msg, envelope.eventId(), envelope.eventType(), source, record.topic(), record.partition(), record.offset());
            return;
        }

        // 업서트로 인박스 저장
        try {
            inboxAppender.upsertRecord(record, source);
        } catch (IllegalStateException e) { // 직렬화,매핑 실패 (재시도 무의미)
            log.warn("[Replies][드롭] 인박스 업서트 실패: {} [eventId={}, type={}, source={}, topic={}, partition={}, offset={}]",
                    e.getMessage(), envelope.eventId(), envelope.eventType(),
                    source, record.topic(), record.partition(), record.offset());
        }

    }

    private static String summarize(ConstraintViolationException ex) {
        return ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("violations");
    }
}
