package msa.bookcatalog.infra.outbox.repository;

import msa.common.events.outbox.OutboxEventRecordStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BookCatalogOutboxEventRecordRepositoryCustom {

    Long updateStatus(Long eventId,
                      OutboxEventRecordStatus newStatus,
                      Collection<OutboxEventRecordStatus> oldStatuses);

    Long markAsFailed(Long eventId, String errorMessage);
    Long markAsDeadLetter(Long eventId, String errorMessage);
    List<BookCatalogOutboxEventRecord> findEventsToRetryWithSkipLock(
            int maxRetry, int limit, LocalDateTime staleThreshold, LocalDateTime timeoutThreshold);

}
