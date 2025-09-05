package msa.bookcatalog.infra.outbox.repository;

import msa.common.events.outbox.OutboxEventRecordStatus;

import java.util.Collection;

public interface BookCatalogOutboxEventRecordRepositoryCustom {

    Long updateStatus(Long eventId,
                      OutboxEventRecordStatus newStatus,
                      Collection<OutboxEventRecordStatus> oldStatuses);

    Long markAsFailed(Long eventId, String errorMessage);
    Long markAsDeadLetter(Long eventId, String errorMessage);
}
