package msa.bookcatalog.infra.outbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.outbox.OutboxEventSender;
import msa.bookcatalog.infra.outbox.config.OutboxSchedulerProperties;
import msa.bookcatalog.infra.outbox.recorder.EventRecorder;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecord;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecordRepository;
import msa.bookcatalog.infra.outbox.service.OutboxClaimerService;
import msa.common.events.outbox.OutboxEventRecordStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookCatalogOutboxRelayScheduler {

    private final EventRecorder eventRecorder;
    private final OutboxSchedulerProperties properties;
    private final OutboxEventSender outboxEventSender;
    private final OutboxClaimerService outboxClaimerService;

    @Scheduled(fixedDelay = 60000)
    public void retryPendingOutboxEvents() {
        List<BookCatalogOutboxEventRecord> targets = outboxClaimerService.claimEvents();

        if (targets.isEmpty()) {
            return;
        }
        log.info("{}개의 아웃박스 이벤트를 재처리합니다.", targets.size());

        for (BookCatalogOutboxEventRecord record : targets) {
            if (isDeadLetterCondition(record)) {
                // 재시도 횟수 초과 시, 데드 레터로 보내고 이번 루프 종료
                String reason = "최대 재시도 횟수(" + properties.maxRetryCount() + "회)를 초과했습니다.";
                eventRecorder.markAsDeadLetter(record.getEventId(), reason);
                continue; // 다음 레코드로 넘어감
            }
            outboxEventSender.resend(record);
        }

    }

    private boolean isDeadLetterCondition(BookCatalogOutboxEventRecord record) {
        // 상태가 FAILED이고, 재시도 횟수가 최대치를 넘었는지 확인
        return record.getOutboxEventRecordStatus() == OutboxEventRecordStatus.FAILED &&
                record.getRetryCount() >= properties.maxRetryCount();
    }


}



