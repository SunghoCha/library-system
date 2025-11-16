package msa.inventory.adaptor.out.persistence.inbox;

import lombok.RequiredArgsConstructor;
import msa.common.config.properties.InboxSchedulerProps;
import msa.common.snowflake.InstanceIdentity;
import msa.inventory.adaptor.out.persistence.inbox.entity.InboxEventRecord;
import msa.inventory.adaptor.out.persistence.inbox.repository.InboxEventRecordRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InboxClaimerService {

    private final Clock clock;
    private final InstanceIdentity instanceIdentity;
    private final InboxSchedulerProps props;
    private final InboxEventRecordRepository inboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<InboxEventRecord> claimEvents() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime leaseUntil = now.plus(props.lease());

        List<Long> ids = inboxRepository.lockClaimableInboxIds(props.batchSize(), props.maxRetryCount(), now);

        if (ids.isEmpty()) return List.of();

        String leaseId = UUID.randomUUID().toString();
        String workerId = instanceIdentity.workerId();

        long updated = inboxRepository.markProcessing(ids, leaseId, workerId, now, leaseUntil);
        if (updated == 0) return List.of();

        return inboxRepository.findProcessingByIdsOrderByUpdatedAt(ids);
    }
}
