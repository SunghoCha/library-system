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

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanSagaTimeoutClaimerService {

    private final Clock clock;
    private final LoanTimeoutProps props;
    private final InstanceIdentity instanceIdentity;
    private final LoanSagaRepository sagaRepository;

    // TODO : 선점할 떄 leaseId 사용하도록 해야하는데 누락된듯?
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> claimProcessingTimeouts() {
        String workerId = instanceIdentity.workerId();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime leaseUntil = now.plusSeconds(props.leaseSecond());
        int batch = props.batchSize();

        List<String> ids = sagaRepository.lockTimedOutProcessingIds(now, batch);
        if (ids.isEmpty()) return List.of();

        sagaRepository.claimByIds(ids, workerId, leaseUntil);
        log.info("[SagaTimeout] PROCESSING 선점: count={}, workerId={}, leaseUntil={}",
                ids.size(), workerId, leaseUntil);

        return ids;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> claimCompensatingTimeouts() {
        String workerId = instanceIdentity.workerId();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime leaseUntil = now.plusSeconds(props.leaseSecond());

        List<String> ids = sagaRepository.lockTimedOutCompensatingIds(now, props.batchSize());
        if (ids.isEmpty()) return List.of();

        sagaRepository.claimByIds(ids, workerId, leaseUntil);
        log.info("[SagaTimeout] COMPENSATING 선점: count={}, workerId={}, leaseUntil={}",
                ids.size(), workerId, leaseUntil);

        return ids;
    }
}
