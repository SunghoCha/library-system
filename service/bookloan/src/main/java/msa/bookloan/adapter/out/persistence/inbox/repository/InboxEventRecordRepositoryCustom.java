package msa.bookloan.adapter.out.persistence.inbox.repository;

import msa.bookloan.adapter.out.persistence.inbox.entity.InboxEventRecord;
import msa.common.events.inbox.dto.InboxEventRecordStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface InboxEventRecordRepositoryCustom {
    long updateStatusIfPending(Long eventId,
                               InboxEventRecordStatus newStatus,
                               Collection<InboxEventRecordStatus> oldStatuses);

    long incrementRetryCountIfBelowMax(Long eventId, int maxRetryCount, String lastError);

    List<InboxEventRecord> findProcessingByIdsOrderByLastSeen(Collection<Long> ids);

    long markProcessing(Collection<Long> ids, String workerId, LocalDateTime pickedAt, LocalDateTime leaseUntil);

    long markProcessedByEventId(Long eventId, String leaseId, String workerId, LocalDateTime pickedAt, LocalDateTime now);

    long markFailedByEventId(Long eventId, String workerId, LocalDateTime pickedAt, String lastError, LocalDateTime now);

    long markDeadFromFailed(Long eventId, String reason, LocalDateTime now);

    long markDeadLetter(Long eventId, String workerId, LocalDateTime pickedAt, String reason, LocalDateTime now);
}
