package msa.bookcatalog.adapter.out.messaging.outbox;

import lombok.RequiredArgsConstructor;
import msa.bookcatalog.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ImmediateClaimer {

    private final OutboxEventRecordRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(Long eventId, String workerId, LocalDateTime now, Duration leaseSec) {
        return outboxRepository.tryClaimFromNew(eventId, workerId, now, leaseSec) > 0;
    }

}
