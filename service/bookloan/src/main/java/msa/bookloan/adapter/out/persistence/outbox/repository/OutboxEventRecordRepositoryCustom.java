package msa.bookloan.adapter.out.persistence.outbox.repository;

import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OutboxEventRecordRepositoryCustom {

    List<OutboxEventRecord> findPublishingByIdsOrderByOccurredAt(Collection<Long> ids);

    long tryClaimFromNew(Long eventId, String workerId, LocalDateTime now, int leaseSeconds);

    long markPublishing(Collection<Long> ids,
                        String leaseId,
                        String workerId,
                        LocalDateTime now,
                        LocalDateTime leaseUntil);

    long markPublished(Collection<Long> ids,
                       String leaseId,
                       LocalDateTime now);

    long markFailed(Collection<Long> ids,
                    String leaseId,
                    String lastError,
                    LocalDateTime now);

    long markDeadFromFailed(Long eventId, String reason, LocalDateTime now);

    long markPublishedByEventId(Long eventId,
                                String leaseId,
                                LocalDateTime now);

    long markFailedByEventId(Long eventId,
                             String leaseId,
                             String lastError,
                             LocalDateTime now);


}
