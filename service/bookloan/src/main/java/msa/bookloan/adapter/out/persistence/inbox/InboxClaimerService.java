package msa.bookloan.adapter.out.persistence.inbox;


import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.persistence.inbox.entity.InboxEventRecord;
import msa.bookloan.adapter.out.persistence.inbox.repository.InboxEventRecordRepository;
import msa.common.config.properties.InboxSchedulerProps;
import msa.common.snowflake.InstanceIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InboxClaimerService {

    private final Clock clock;
    private final InstanceIdentity instanceIdentity;
    private final InboxSchedulerProps props;
    private final InboxEventRecordRepository inboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<InboxEventRecord> claimEvents() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime staleAt = now.minus(props.staleTimeout());
        LocalDateTime leaseUntil = now.plus(props.lease());

        List<Long> ids = inboxRepository.lockClaimableInboxIds(
                props.batchSize(), props.maxRetryCount(), now, staleAt
        );

        if (ids.isEmpty()) return List.of();

        String leaseId = UUID.randomUUID().toString();
        String workerId = instanceIdentity.workerId();

        long updated = inboxRepository.markProcessing(ids, leaseId, workerId, now, leaseUntil);
        if (updated == 0) return List.of();

        return inboxRepository.findProcessingByIdsOrderByUpdatedAt(ids);
    }
}
