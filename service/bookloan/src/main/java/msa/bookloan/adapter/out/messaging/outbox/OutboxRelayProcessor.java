package msa.bookloan.adapter.out.messaging.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.outbox.EventRecorder;
import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.common.config.properties.OutboxSchedulerProps;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayProcessor {

    private final EventRecorder eventRecorder;
    private final OutboxSchedulerProps props;
    private final OutboxEventRecordRepository outboxEventRecordRepository;

    public void updateStatusAfterProcessing(Long eventId, String leaseId, Throwable ex) {
        if (ex == null) {
            eventRecorder.markPublishedByEventId(eventId, leaseId);
            return;
        }

        int attempt = outboxEventRecordRepository.findByEventId(eventId)
                .map(OutboxEventRecord::getRetryCount)
                .orElse(0) + 1;

        long updated = eventRecorder.markFailedByEventId(eventId, leaseId, ex.toString());// 미리 FAILED로 해야 DEAD 가능

        if (updated > 0 && attempt >= props.maxRetryCount()) { //
            eventRecorder.markDeadLetter(eventId, "재시도 횟수 초과: " + ex);
        }
    }
}


