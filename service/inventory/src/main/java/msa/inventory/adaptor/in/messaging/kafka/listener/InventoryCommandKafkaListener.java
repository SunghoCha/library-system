package msa.inventory.adaptor.in.messaging.kafka.listener;


import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.common.events.MessageEnvelope;
import msa.common.util.EventPayloadValidator;
import msa.inventory.adaptor.out.persistence.inbox.recorder.InboxAppender;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class InventoryCommandKafkaListener {

    private final InboxAppender inboxAppender;
    private final EventPayloadValidator payloadValidator;

    @KafkaListener(

    )
    @Transactional
    public void onReserveInventory(ConsumerRecord<String, MessageEnvelope> record) {
        log.debug("카프카 레코드 수신: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        MessageEnvelope envelope = record.value();

        if (envelope == null) {
            log.warn("[Replies][드롭] envelope=null (topic={}, partition={}, offset={})",
                    record.topic(), record.partition(), record.offset());
            return;
        }

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

    private static String summarize(ConstraintViolationException e) {
        return e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("violations");
    }

}
