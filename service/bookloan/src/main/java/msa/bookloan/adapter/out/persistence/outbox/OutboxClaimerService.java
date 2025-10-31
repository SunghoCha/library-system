package msa.bookloan.adapter.out.persistence.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.common.config.properties.OutboxSchedulerProps;
import msa.common.snowflake.InstanceIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxClaimerService {

    private final Clock clock;
    private final InstanceIdentity instanceIdentity;
    private final OutboxSchedulerProps props;
    private final OutboxEventRecordRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEventRecord> claimEvents() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime leaseUntil = now.plus(props.lease());

        log.debug("[Outbox][CLAIM] 후보 스캔: batch={}, maxRetry={}, now={}",
                props.batchSize(), props.maxRetryCount(), now);

        List<Long> ids = outboxRepository.lockClaimableIds(
                props.batchSize(),
                props.maxRetryCount(),
                now
        );

        if (ids.isEmpty()) {
            log.debug("[Outbox][CLAIM] 후보 없음");
            return List.of();
        }

        String workerId = instanceIdentity.workerId();
        String leaseId = UUID.randomUUID().toString();
        long updated = outboxRepository.markPublishing(ids, leaseId, workerId, leaseUntil);

        if (updated == 0) {
            log.debug("[Outbox][CLAIM] 선점 실패(경합): requestCount={}, workerId={}", ids.size(), workerId);
            return List.of();
        }

        return outboxRepository.findPublishingByIdsOrderByOccurredAt(ids);
    }
}
