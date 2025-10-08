package msa.bookcatalog.infra.messaging.outbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.messaging.outbox.config.OutboxSchedulerProperties;
import msa.bookcatalog.infra.messaging.outbox.OutboxEventSender;
import msa.bookcatalog.infra.messaging.outbox.recorder.EventRecorder;
import msa.bookcatalog.infra.messaging.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.infra.messaging.outbox.service.OutboxClaimerService;
import msa.common.events.outbox.OutboxEventRecordStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = false)
public class OutboxRelayScheduler {

    private final EventRecorder eventRecorder;
    private final OutboxSchedulerProperties properties;
    private final OutboxEventSender outboxEventSender;
    private final OutboxClaimerService outboxClaimerService;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:60000}")
    public void retryPendingOutboxEvents() {
        List<OutboxEventRecord> targets = outboxClaimerService.claimEvents();

        if (targets.isEmpty()) {
            return;
        }
        log.info("{}개의 아웃박스 이벤트를 재처리합니다.", targets.size());

        for (OutboxEventRecord record : targets) {
            if (isDeadLetterCondition(record)) {
                // 재시도 횟수 초과 시, 데드 레터로 보내고 이번 루프 종료
                String reason = "최대 재시도 횟수(" + properties.maxRetryCount() + "회)를 초과했습니다.";
                eventRecorder.markAsDeadLetter(record.getEventId(), reason);
                continue; // 다음 레코드로 넘어감
            }
            outboxEventSender.resend(record);
        }

    }

    private boolean isDeadLetterCondition(OutboxEventRecord record) {
        // 상태가 FAILED이고, 재시도 횟수가 최대치를 넘었는지 확인
        return record.getOutboxEventRecordStatus() == OutboxEventRecordStatus.FAILED &&
                record.getRetryCount() >= properties.maxRetryCount();
    }


}



