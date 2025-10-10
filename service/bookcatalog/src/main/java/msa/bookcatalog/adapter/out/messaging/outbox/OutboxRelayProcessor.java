package msa.bookcatalog.adapter.out.messaging.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookcatalog.infra.config.properties.OutboxSchedulerProps;
import msa.bookcatalog.adapter.out.persistence.outbox.EventRecorder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayProcessor {

    private final EventRecorder eventRecorder;
    private final OutboxSchedulerProps props;
    private final OutboxEventRecordRepository outboxEventRecordRepository;

    public void updateStatusAfterProcessing(Long eventId,
                                            String workerId,
                                            LocalDateTime claimedAt,
                                            Throwable ex) {
        if (ex == null) {
            eventRecorder.markPublishedByEventId(eventId, workerId, claimedAt);
            return;
        }

        int attempt = outboxEventRecordRepository.findByEventId(eventId)
                .map(OutboxEventRecord::getRetryCount)
                .orElse(0) + 1;

        int updated = eventRecorder.markFailedByEventId(eventId, workerId, claimedAt, ex.toString());// 미리 FAILED로 해야 DEAD 가능
        if (updated > 0 && attempt >= props.maxRetryCount()) { // ★ 실패가 실제 반영된 경우에만 DEAD
            eventRecorder.markDeadLetter(eventId, "재시도 횟수 초과: " + ex);
        }
    }

}
