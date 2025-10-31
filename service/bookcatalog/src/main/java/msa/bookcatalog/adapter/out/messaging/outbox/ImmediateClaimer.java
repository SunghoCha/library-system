package msa.bookcatalog.adapter.out.messaging.outbox;

import lombok.RequiredArgsConstructor;
import msa.bookcatalog.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.common.config.properties.OutboxSchedulerProps;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ImmediateClaimer {

    private final OutboxEventRecordRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(Long eventId, String workerId, String leaseId, LocalDateTime now, LocalDateTime leaseUntil) {
        return outboxRepository.tryClaimFromNew(eventId, workerId, leaseId, now, leaseUntil) > 0;
    }

}
