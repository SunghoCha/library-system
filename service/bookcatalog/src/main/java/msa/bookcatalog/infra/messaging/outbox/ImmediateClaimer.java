package msa.bookcatalog.infra.messaging.outbox;

import lombok.RequiredArgsConstructor;
import msa.bookcatalog.infra.messaging.outbox.repository.OutboxEventRecordRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ImmediateClaimer {

    private final OutboxEventRecordRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(Long eventId, String workerId, LocalDateTime now, int leaseSec) {
        return outboxRepository.tryClaimFromNew(eventId, workerId, now, leaseSec) > 0;
    }

}
