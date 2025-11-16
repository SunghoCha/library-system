package msa.bookloan.adapter.out.persistence.inbox.recorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.inbox.repository.InboxEventRecordRepository;
import msa.common.config.properties.InboxProcessingProps;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.abbreviate;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboxStatusMarker {

    private final InboxEventRecordRepository eventRecordRepository;
    private final InboxProcessingProps inboxProcessingProps;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY) // 기존 트랜잭션 합류해야함
    public void markProcessed(Long eventId, String leaseId) {
        Objects.requireNonNull(eventId,  "eventId");
        Objects.requireNonNull(leaseId, "leaseId");

        LocalDateTime now = LocalDateTime.now(clock);
        long updated = eventRecordRepository.markProcessedByEventId(eventId, leaseId, now);

        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                    "Inbox token mismatch: eventId=" + eventId + ", leaseId=" + leaseId);
        }
        log.info("[Inbox] PROCESSED 마킹 완료: eventId={}, leaseId={}", eventId, leaseId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long eventId, String leaseId, String reason) {
        Objects.requireNonNull(eventId,  "eventId");
        Objects.requireNonNull(leaseId, "leaseId");

        LocalDateTime now = LocalDateTime.now(clock);
        String safeReason = abbreviate(reason, inboxProcessingProps.errorMaxLength());
        long updated = eventRecordRepository.markFailedByEventId(eventId, leaseId, safeReason, now);

        if (updated == 1) {
            log.warn("[Inbox] 처리 실패로 마킹됨(재시도 예정) (eventId={}, leaseId={}, reason={})", eventId, leaseId, safeReason);
        } else {
            log.info("[Inbox] 실패 마킹 스킵(상태 불일치/토큰 불일치) (eventId={}, leaseId={})", eventId, leaseId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDeadLetter(Long eventId, String leaseId, String reason) {
        Objects.requireNonNull(eventId,  "eventId");
        Objects.requireNonNull(leaseId, "leaseId");

        LocalDateTime now = LocalDateTime.now(clock);
        String safeReason = abbreviate(reason, inboxProcessingProps.errorMaxLength());
        long updated = eventRecordRepository.markDeadLetter(eventId, leaseId, safeReason, now);

        if (updated == 1) {
            log.warn("[Inbox] DLT로 마킹됨 (eventId={}, leaseId={}, reason={})", eventId, leaseId, safeReason);
        } else {
            log.info("[Inbox] DLT 마킹 스킵(상태 불일치/토큰 불일치) (eventId={}, leaseId={})", eventId, leaseId);
        }
    }

}
