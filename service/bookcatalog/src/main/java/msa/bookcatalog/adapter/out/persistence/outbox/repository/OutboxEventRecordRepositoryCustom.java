package msa.bookcatalog.adapter.out.persistence.outbox.repository;

import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OutboxEventRecordRepositoryCustom {

    List<OutboxEventRecord> findPublishingByIdsOrderByOccurredAt(Collection<Long> ids);

    long tryClaimFromNew(Long eventId, String workerId, LocalDateTime now, int leaseSeconds);

    long markPublishing(Collection<Long> ids,
                            String workerId,
                            LocalDateTime now,
                            int leaseSeconds);

    long markPublished(Collection<Long> ids,
                       String workerId,
                       LocalDateTime claimedAt);

    long markFailed(Collection<Long> ids,
                    String workerId,
                    LocalDateTime claimedAt,
                    String lastError);

    long markDeadFromFailed(Long eventId, String reason);

    long markPublishedByEventId(Long eventId,
                                String workerId,
                                LocalDateTime claimedAt);

    long markFailedByEventId(Long eventId,
                             String workerId,
                             LocalDateTime claimedAt,
                             String lastError);


}
