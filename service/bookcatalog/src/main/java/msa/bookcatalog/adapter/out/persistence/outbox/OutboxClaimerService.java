package msa.bookcatalog.adapter.out.persistence.outbox;

import lombok.RequiredArgsConstructor;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.common.config.properties.OutboxSchedulerProps;
import msa.common.snowflake.InstanceIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxClaimerService {

    private final InstanceIdentity instanceIdentity;
    private final OutboxSchedulerProps props;
    private final OutboxEventRecordRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEventRecord> claimEvents() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plus(props.lease());
        String leaseId = UUID.randomUUID().toString();
        List<Long> ids = outboxRepository.lockClaimableIds(
                props.batchSize(),
                props.maxRetryCount(),
                now);

        if (ids.isEmpty()) {
            return List.of();
        }

        String workerId = instanceIdentity.workerId();

        long updated = outboxRepository.markPublishing(ids, workerId, leaseId, now, leaseUntil);
        if (updated == 0) {
            return List.of();
        }

        return outboxRepository.findPublishingByIdsOrderByOccurredAt(ids);
    }
}
