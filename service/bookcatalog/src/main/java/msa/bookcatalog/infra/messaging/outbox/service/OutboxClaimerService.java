package msa.bookcatalog.infra.messaging.outbox.service;

import lombok.RequiredArgsConstructor;
import msa.bookcatalog.infra.messaging.outbox.config.OutboxSchedulerProperties;
import msa.bookcatalog.infra.messaging.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.infra.messaging.outbox.repository.OutboxEventRecordRepository;
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
    private final OutboxEventRecordRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEventRecord> claimEvents() {
        LocalDateTime gracePeriodThreshold = LocalDateTime.now().minusMinutes(properties.gracePeriodMinutes());
        LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(properties.staleTimeoutMinutes());

        List<OutboxEventRecord> eventsToRetry = outboxRepository.findEventsToRetryWithSkipLock(
                properties.maxRetryCount(),
                properties.batchSize(),
                staleThreshold,
                gracePeriodThreshold
        );

        if (eventsToRetry.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> eventIds = eventsToRetry.stream()
                .map(OutboxEventRecord::getEventId)
                .collect(Collectors.toList());

        outboxRepository.updateStatusToPublishing(eventIds,
                LocalDateTime.now(),
                OutboxEventRecordStatus.CLAIMABLE_STATUSES
        );

        return eventsToRetry;
    }
}
