package msa.bookloan.adapter.in.messaging.kafka.listener;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.kafka.util.InboxSourceResolver;
import msa.bookloan.adapter.in.messaging.kafka.util.ReplyPayloadTranslator;
import msa.bookloan.adapter.in.messaging.kafka.util.validator.EventPayloadValidator;
import msa.bookloan.adapter.out.persistence.inbox.recorder.DeadLetterAppender;
import msa.bookloan.adapter.out.persistence.inbox.recorder.InboxAppender;
import msa.bookloan.application.event.SagaReplyEnvelope;
import msa.common.domain.model.InboxSource;
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
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class SagaReplyKafkaListener {

    private final InboxAppender inboxAppender;
    private final InboxSourceResolver inboxSourceResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final DeadLetterAppender deadLetterAppender;

    private final EventPayloadValidator payloadValidator;
    private final ReplyPayloadTranslator payloadTranslator;

    @KafkaListener(
            topics = "${app.kafka.topic-saga-replies}",
            groupId = "${app.kafka.group-saga-replies}"
    )
    @Transactional
    public void onSagaReply(ConsumerRecord<String, SagaReplyEnvelope> record) {
        log.debug("카프카 레코드 수신: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        InboxSource source = inboxSourceResolver.resolveFromTopic(record.topic());
        SagaReplyEnvelope payload = record.value();

        // 페이로드 null체크
        if (payload == null) {
            log.warn("[Replies][드롭] 페이로드가 null 입니다. source={}, topic={}, partition={}, offset={}",
                    source, record.topic(), record.partition(), record.offset());
            return;
        }

        // 유효성 검증
        try {
            payloadValidator.validateOrThrow(payload);
        } catch (ConstraintViolationException e) {
            String msg = summarize(e);
            log.warn("[Replies][드롭] 유효성 검증 실패: {} [eventId={}, sagaId={}, type={}, source={}, topic={}, partition={}, offset={}]",
                    msg, payload.eventId(), payload.sagaId(), payload.replyType(),
                    source, record.topic(), record.partition(), record.offset());
            return;
        }

        // 업서트로 인박스 저장
        boolean isNew;
        try {
            isNew = inboxAppender.upsertRecord(record, source);
        } catch (IllegalStateException e) { // 직렬화,매핑 실패 (재시도 무의미)
            log.warn("[Replies][드롭] 인박스 업서트 실패: {} [eventId={}, sagaId={}, type={}, source={}, topic={}, partition={}, offset={}]",
                    e.getMessage(), payload.eventId(), payload.sagaId(), payload.replyType(),
                    source, record.topic(), record.partition(), record.offset());
            return;
        }

        // TODO : 삭제 해야할듯. 더이상 발행 방식 아님
        // 스프링 내부 이벤트 발행
        if (isNew) {
            log.info("[Replies] 내부 이벤트 발행: sagaId={}, type={}, eventId={}",
                    payload.sagaId(), payload.replyType(), payload.eventId());
            eventPublisher.publishEvent(payloadTranslator.toInternalEvent(payload));
        } else {
            log.debug("[Replies] 중복/재처리 스킵: sagaId={}, type={}, eventId={}",
                    payload.sagaId(), payload.replyType(), payload.eventId());
        }
    }

    private static String summarize(ConstraintViolationException ex) {
        return ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("violations");
    }
}
