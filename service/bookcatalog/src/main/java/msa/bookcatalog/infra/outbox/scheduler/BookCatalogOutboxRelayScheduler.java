package msa.bookcatalog.infra.outbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.outbox.OutboxEventSender;
import msa.bookcatalog.infra.outbox.recorder.EventRecorder;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecord;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecordRepository;
import msa.bookcatalog.infra.outbox.service.OutboxClaimerService;
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

    // TODO : 추후 설정값 외부로 분리
    private static final int MAX_RETRY_COUNT = 3;
    private static final int BATCH_SIZE = 20;
    private static final int PROCESSING_TIMEOUT_MINUTES = 3;

    private final BookCatalogOutboxEventRecordRepository eventRecordRepository;
    private final OutboxEventSender outboxEventSender;
    private final OutboxClaimerService outboxClaimerService;

    // 실패 데이터가 적을 것이라는 전제로 배치에 트랜잭션. 비관적 락 사용
    @Transactional
    @Scheduled(fixedDelayString = "${outbox.relay.delay:60000}", initialDelayString = "${outbox.relay.initialDelay:10000}")
    public void retryOutboxMessages() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(PROCESSING_TIMEOUT_MINUTES);
        List<BookCatalogOutboxEventRecord> targetList =
                eventRecordRepository.findEventsToRetry(
                        timeoutThreshold,
                        MAX_RETRY_COUNT,
                        PageRequest.of(0, BATCH_SIZE));

        if (!targetList.isEmpty()) {
            log.info("Retrying {} outbox events.", targetList.size());
        }

        for (BookCatalogOutboxEventRecord eventRecord : targetList) {
            outboxEventSender.resend(eventRecord);
        }

    }

    @Scheduled(fixedDelay = 60000)
    public void retryPendingOutboxEvents() {
        List<BookCatalogOutboxEventRecord> targets = outboxClaimerService.claimEvents();

        if (targets.isEmpty()) {
            return;
        }
        log.info("{}개의 아웃박스 이벤트를 재처리합니다.", targets.size());

        for (BookCatalogOutboxEventRecord record : targets) {
            outboxEventSender.resend(record);
        }
    }

}



