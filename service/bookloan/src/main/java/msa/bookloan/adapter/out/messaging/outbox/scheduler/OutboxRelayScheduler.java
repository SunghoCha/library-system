package msa.bookloan.adapter.out.messaging.outbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.messaging.outbox.OutboxEventSender;
import msa.bookloan.adapter.out.persistence.outbox.OutboxClaimerService;
import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = false)
public class OutboxRelayScheduler {

    private final OutboxEventSender outboxEventSender;
    private final OutboxClaimerService outboxClaimerService;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:60000}")
    public void retryPendingOutboxEvents() {
        List<OutboxEventRecord> claimedRecords = outboxClaimerService.claimEvents();

        if (claimedRecords.isEmpty()) return;

        log.info("{}개의 아웃박스 이벤트를 재처리합니다.", claimedRecords.size());
        for (OutboxEventRecord record : claimedRecords) {
            try {
                outboxEventSender.send(record);
            } catch (Exception e) {
                log.info("재발행 에러 id={}, eventId={}", record.getId(), record.getEventId(), e);
            }
        }

    }

}



