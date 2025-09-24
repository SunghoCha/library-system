package msa.bookcatalog.infra.outbox.service;

import lombok.RequiredArgsConstructor;
import msa.bookcatalog.infra.outbox.config.OutboxSchedulerProperties;
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

    private final OutboxSchedulerProperties properties;
    private final BookCatalogOutboxEventRecordRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<BookCatalogOutboxEventRecord> claimEvents() {
        LocalDateTime gracePeriodThreshold = LocalDateTime.now().minusMinutes(properties.gracePeriodMinutes());
        LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(properties.staleTimeoutMinutes());

        List<BookCatalogOutboxEventRecord> eventsToRetry = outboxRepository.findEventsToRetryWithSkipLock(
                properties.maxRetryCount(),
                properties.batchSize(),
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
