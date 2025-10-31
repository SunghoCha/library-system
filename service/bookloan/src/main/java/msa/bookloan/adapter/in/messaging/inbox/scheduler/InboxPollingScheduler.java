package msa.bookloan.adapter.in.messaging.inbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.inbox.InboxEventDispatcher;
import msa.bookloan.adapter.out.persistence.inbox.InboxClaimerService;
import msa.bookloan.adapter.out.persistence.inbox.entity.InboxEventRecord;
import msa.common.snowflake.InstanceIdentity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboxPollingScheduler {

    private final InboxClaimerService claimerService;
    private final InboxEventDispatcher inboxEventDispatcher;
    private final InstanceIdentity instanceIdentity;

    @Scheduled(fixedDelayString = "${inbox.retry.delay:60000}")
    public void pollAndProcess() {
        String workerId = instanceIdentity.workerId();

        List<InboxEventRecord> claimedRecords;
        try {
             claimedRecords = claimerService.claimEvents();
        } catch (Exception e) {
            log.error("[Inbox] 선점 실패(workerId={})", workerId, e);
            return;
        }

        if (claimedRecords.isEmpty()) {
            log.debug("[Inbox] 처리 대상 없음(workerId={})", workerId);
            return;
        }
        log.info("[Inbox] 배치 시작: count={}, workerId={}", claimedRecords.size(), workerId);

        int successCount = 0;
        int failureCount = 0;
        for (InboxEventRecord record : claimedRecords) {
            try {
                inboxEventDispatcher.processEvent(record.getEventId(), record.getLeaseId());
                successCount++;
            } catch (OptimisticLockingFailureException ole) {
                // 경합은 정상 플로우니까 실패 카운트에 안 넣고 조용히 넘기는게 나은거 같음
                log.debug("[Inbox] 처리권 상실(경합): eventId={}", record.getEventId());

            } catch (RuntimeException e) {
                failureCount++;
                log.warn("[Inbox] 처리 실패(T_Main rolled back): eventId={}, workerId={}",
                        record.getEventId(), workerId, e);
            }
        }

        if (failureCount > 0) {
            log.warn("[Inbox] 배치 종료: success={}, failure={}, workerId={}",
                    successCount, failureCount, workerId);
        } else {
            log.info("[Inbox] 배치 종료: success={}, workerId={}",
                    successCount, workerId);
        }
    }

}
