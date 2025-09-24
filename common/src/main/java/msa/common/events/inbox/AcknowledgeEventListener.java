package msa.common.events.inbox;

import lombok.extern.slf4j.Slf4j;
import msa.common.events.inbox.dto.AcknowledgeEvent;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class AcknowledgeEventListener {

    // TODO : 필요없어서 삭제해도 될 듯. 오프셋 커밋 방식 변경했음
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAcknowledge(AcknowledgeEvent event) {
        Acknowledgment ack = event.getAck();
        ack.acknowledge();
        log.debug("Offset acknowledged");
    }

}
