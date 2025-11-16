package msa.inventory.adaptor.out.persistence.inbox.repository;

import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.inventory.adaptor.out.persistence.inbox.entity.InboxEventRecord;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface InboxEventRecordRepositoryCustom {

    long updateStatusIfPending(Long eventId,
                               InboxEventRecordStatus newStatus,
                               Collection<InboxEventRecordStatus> oldStatuses);

    long incrementRetryCountIfBelowMax(Long eventId, int maxRetryCount, String lastError);

    List<InboxEventRecord> findProcessingByIdsOrderByUpdatedAt(Collection<Long> ids);

    long markProcessing(Collection<Long> ids, String leaseId, String workerId, LocalDateTime now, LocalDateTime leaseUntil);

    long markProcessedByEventId(Long eventId, String leaseId, LocalDateTime now);

    long markFailedByEventId(Long eventId, String leaseId, String lastError, LocalDateTime now);

    long markDeadFromFailed(Long eventId, String reason, LocalDateTime now);

    long markDeadLetter(Long eventId, String leaseId, String reason, LocalDateTime now);
}
