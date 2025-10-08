package msa.bookloan.adapter.out.persistence.inbox.repository;

import msa.common.events.inbox.dto.InboxEventRecordStatus;

import java.util.Collection;

public interface InboxEventRecordRepositoryCustom {
    Long updateStatusIfPending(Long eventId,
                               InboxEventRecordStatus newStatus,
                               Collection<InboxEventRecordStatus> oldStatuses);

    Long incrementRetryCountIfBelowMax(Long eventId, int maxRetryCount, String lastError);
}
