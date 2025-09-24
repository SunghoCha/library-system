package msa.bookcatalog.infra.outbox.service;

import lombok.RequiredArgsConstructor;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecord;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecordRepository;
import msa.common.events.outbox.OutboxEventRecordStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OutboxClaimerService {

    // TODO : 변수 외부로 분리하기
    private static final int MAX_RETRY_COUNT = 5;
    private static final int BATCH_SIZE = 100;
    private static final int GRACE_PERIOD_MINUTES = 1;
    private static final int STALE_TIMEOUT_MINUTES = 5;
    
    private final BookCatalogOutboxEventRecordRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<BookCatalogOutboxEventRecord> claimEvents() {
        LocalDateTime gracePeriodThreshold = LocalDateTime.now().minusMinutes(GRACE_PERIOD_MINUTES);
        LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(STALE_TIMEOUT_MINUTES);

        List<BookCatalogOutboxEventRecord> eventsToRetry = outboxRepository.findEventsToRetryWithSkipLock(
                MAX_RETRY_COUNT,
                BATCH_SIZE,
                staleThreshold,
                gracePeriodThreshold
        );

        if (eventsToRetry.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> eventIds = eventsToRetry.stream()
                .map(BookCatalogOutboxEventRecord::getEventId)
                .collect(Collectors.toList());

        outboxRepository.updateStatusToPublishing(eventIds,
                LocalDateTime.now(),
                OutboxEventRecordStatus.CLAIMABLE_STATUSES
        );

        return eventsToRetry;
    }
}
