package msa.bookloan.adapter.out.messaging.outbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.messaging.outbox.OutboxEventSender;
import msa.bookloan.adapter.out.persistence.outbox.OutboxClaimerService;
import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class OutboxPollingScheduler {

    private final OutboxEventSender outboxEventSender;
    private final OutboxClaimerService outboxClaimerService;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:60000}")
    public void pollAndPublish() {
        List<OutboxEventRecord> targets = outboxClaimerService.claimEvents();

        if (targets.isEmpty()) {
            log.debug("[Outbox] 처리할 이벤트 없음");
            return;
        }

        log.info("[Outbox] 배치 시작: {}개의 이벤트를 처리합니다.", targets.size());

        int successCount = 0;
        int failureCount = 0;
        for (OutboxEventRecord record : targets) {
            try {
                outboxEventSender.send(record);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.warn("[Outbox] 발행 실패 (재시도 예정): id={}, eventId={}",
                        record.getId(), record.getEventId(), e);
            }
        }

        if (failureCount > 0) {
            log.warn("[Outbox] 배치 완료: 성공={}, 실패={}, 전체={}",
                    successCount, failureCount, targets.size());
        } else {
            log.info("[Outbox] 배치 완료: 성공={}, 전체={}",
                    successCount, targets.size());
        }

    }

}



