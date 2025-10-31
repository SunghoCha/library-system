package msa.bookloan.adapter.out.persistence.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.infra.config.properties.LoanTimeoutProps;
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
public class LoanSagaTimeoutClaimerService {

    private final Clock clock;
    private final LoanTimeoutProps props;
    private final InstanceIdentity instanceIdentity;
    private final LoanSagaRepository sagaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> claimProcessingTimeouts() {
        LocalDateTime now = LocalDateTime.now(clock);
        String workerId = instanceIdentity.workerId();
        String leaseId = UUID.randomUUID().toString();
        LocalDateTime leaseUntil = now.plusSeconds(props.leaseSecond());

        List<Long> ids = sagaRepository.lockTimedOutProcessingIds(now, props.batchSize());
        if (ids.isEmpty()) return List.of();

        sagaRepository.claimByIds(ids, workerId, leaseId, leaseUntil);
        log.info("[SagaTimeout] PROCESSING 선점: count={}, workerId={}, leaseId={}, leaseUntil={}",
                ids.size(), workerId, leaseId, leaseUntil);

        return ids;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> claimCompensatingTimeouts() {
        LocalDateTime now = LocalDateTime.now(clock);
        String leaseId = UUID.randomUUID().toString();
        String workerId = instanceIdentity.workerId();
        LocalDateTime leaseUntil = now.plusSeconds(props.leaseSecond());

        List<Long> ids = sagaRepository.lockTimedOutCompensatingIds(now, props.batchSize());
        if (ids.isEmpty()) return List.of();

        sagaRepository.claimByIds(ids, workerId, leaseId, leaseUntil);
        log.info("[SagaTimeout] COMPENSATING 선점: count={}, workerId={}, leaseId={}, leaseUntil={}",
                ids.size(), workerId, leaseId, leaseUntil);

        return ids;
    }
}
