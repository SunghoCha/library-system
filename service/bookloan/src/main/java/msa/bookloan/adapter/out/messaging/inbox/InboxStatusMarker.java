package msa.bookloan.adapter.out.messaging.inbox;

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

    private final InboxEventRecordRepository repository;
    private final InboxProcessingProps inboxProcessingProps;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY) // 기존 트랜잭션 합류해야함
    public void markProcessed(Long eventId, String workerId, LocalDateTime pickedAt) {
        Objects.requireNonNull(eventId,  "eventId");
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(pickedAt, "pickedAt");

        LocalDateTime now = LocalDateTime.now(clock);
        long updated = repository.markProcessedByEventId(eventId, workerId, pickedAt, now);

        if (updated != 1) {
            throw new OptimisticLockingFailureException("Inbox token mismatch: eventId=" + eventId + ", workerId=" + workerId);
        }
        log.info("[Inbox] PROCESSED 마킹 완료: eventId={}, workerId={}", eventId, workerId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long eventId, String workerId, LocalDateTime pickedAt, String reason) {
        Objects.requireNonNull(eventId,  "eventId");
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(pickedAt, "pickedAt");

        LocalDateTime now = LocalDateTime.now(clock);
        String safeReason = abbreviate(reason, inboxProcessingProps.errorMaxLength());
        long updated = repository.markFailedByEventId(eventId, workerId, pickedAt, safeReason, now);

        if (updated == 1) {
            log.warn("[Inbox] 처리 실패로 마킹됨(재시도 예정) (eventId={}, workerId={}, reason={})", eventId, workerId, safeReason);
        } else {
            log.info("[Inbox] 실패 마킹 스킵(상태 불일치/토큰 불일치) (eventId={}, workerId={})", eventId, workerId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDeadLetter(Long eventId, String workerId, LocalDateTime pickedAt, String reason) {
        Objects.requireNonNull(eventId,  "eventId");
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(pickedAt, "pickedAt");

        LocalDateTime now = LocalDateTime.now(clock);
        String safeReason = abbreviate(reason, inboxProcessingProps.errorMaxLength());
        long updated = repository.markDeadLetter(eventId, workerId, pickedAt, safeReason, now);

        if (updated == 1) {
            log.warn("[Inbox] DLT로 마킹됨 (eventId={}, workerId={}, reason={})", eventId, workerId, safeReason);
        } else {
            log.info("[Inbox] DLT 마킹 스킵(상태 불일치/토큰 불일치) (eventId={}, workerId={})", eventId, workerId);
        }
    }

    /**
     * FAILED 상태에서 운영자가 강제로 DLT로 넘길 때 사용.
     * (토큰 없이 상태 전이: FAILED -> DEAD_LETTER)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDeadFromFailed(Long eventId, String reason) {
        Objects.requireNonNull(eventId, "eventId");

        LocalDateTime now = LocalDateTime.now(clock);
        String safeReason = abbreviate(reason, inboxProcessingProps.errorMaxLength());
        long updated = repository.markDeadFromFailed(eventId, safeReason, now);

        if (updated == 1) {
            log.warn("[Inbox] FAILED -> DLT 전환 완료 (eventId={}, reason={})", eventId, safeReason);
        } else {
            log.info("[Inbox] FAILED -> DLT 전환 스킵(상태 불일치) (eventId={})", eventId);
        }
    }


}
