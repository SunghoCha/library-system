package msa.bookloan.adapter.out.persistence.inbox.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.persistence.inbox.entity.InboxEventRecord;
import msa.bookloan.adapter.out.persistence.inbox.entity.QInboxEventRecord;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static msa.common.events.inbox.dto.InboxEventRecordStatus.FAILED;
import static msa.common.events.inbox.dto.InboxEventRecordStatus.PROCESSING;

@Repository
@Transactional
@RequiredArgsConstructor
public class InboxEventRecordRepositoryImpl implements InboxEventRecordRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QInboxEventRecord record = QInboxEventRecord.inboxEventRecord;

    @Override
    public long updateStatusIfPending(Long eventId, InboxEventRecordStatus newStatus, Collection<InboxEventRecordStatus> oldStatuses) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(newStatus, "newStatus");
        if (oldStatuses == null || oldStatuses.isEmpty()) {
            return 0L;
        }

        return queryFactory
                .update(record)
                .set(record.inboxEventRecordStatus, newStatus)
                .where(
                        eqEventIdOrFalse(eventId),
                        statusInOrFalse(oldStatuses)
                )
                .execute();
    }

    public long incrementRetryCountIfBelowMax(Long eventId, int maxRetryCount, String lastError) {
        Objects.requireNonNull(eventId, "eventId");

        return queryFactory
                .update(record)
                .set(record.retryCount, record.retryCount.add(1))
                .set(record.lastError, lastError)
                .where(
                        eqEventIdOrFalse(eventId),
                        record.retryCount.lt(maxRetryCount)
                )
                .execute();
    }

    @Override
    public List<InboxEventRecord> findProcessingByIdsOrderByUpdatedAt(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();

        return queryFactory.
                selectFrom(record).
                where(
                        idInOrNull(ids),
                        eqStatusOrNull(PROCESSING)
                )
                .orderBy(record.updatedAt.asc(), record.id.asc())
                .fetch();
    }

    // 스킵락 건 상태에서 사용하는 메서드라 where조건이 간단한 상태임에 주의 (범용적이지 않음)
    @Override
    public long markProcessing(Collection<Long> ids, String leaseId, String workerId,
                               LocalDateTime now, LocalDateTime leaseUntil) {
        Objects.requireNonNull(leaseId, "leaseId");
        Objects.requireNonNull(workerId,  "workerId");
        Objects.requireNonNull(now,  "now");
        Objects.requireNonNull(leaseUntil,"leaseUntil");

        // 빈 배치로 오면 0건 업데이트니까 의미적으로 맞음
        if (ids == null || ids.isEmpty()) return 0L;

        return queryFactory
                .update(record)
                .set(record.inboxEventRecordStatus, PROCESSING)
                .set(record.leaseId, leaseId)
                .set(record.workerId, workerId)
                .set(record.leaseUntil, leaseUntil)
                .set(record.updatedAt, now)
                .where(idInOrFalse(ids))
                .execute();
    }

    @Override
    public long markProcessedByEventId(Long eventId, String leaseId, LocalDateTime now) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseId, "leaseId");
        Objects.requireNonNull(now, "now");

        return queryFactory
                .update(record)
                .set(record.inboxEventRecordStatus, InboxEventRecordStatus.PROCESSED)
                .setNull(record.workerId)
                .setNull(record.leaseUntil)
                .setNull(record.leaseId)
                .setNull(record.lastError)
                .set(record.updatedAt, now)
                .where(
                        eqEventIdOrFalse(eventId),
                        eqStatusOrFalse(PROCESSING),
                        eqLeaseIdOrFalse(leaseId)
                )
                .execute();
    }

    @Override
    public long markFailedByEventId(Long eventId, String leaseId, String lastError, LocalDateTime now) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseId, "leaseId");
        Objects.requireNonNull(now, "now");

        return queryFactory
                .update(record)
                .set(record.inboxEventRecordStatus, InboxEventRecordStatus.FAILED)
                .set(record.retryCount, record.retryCount.add(1))
                .setNull(record.workerId)
                .setNull(record.leaseUntil)
                .setNull(record.leaseId)
                .set(record.lastError, lastError)
                .set(record.updatedAt, now)
                .where(
                        eqEventIdOrFalse(eventId),
                        eqStatusOrFalse(PROCESSING),
                        eqLeaseIdOrFalse(leaseId)
                )
                .execute();
    }

    @Override
    public long markDeadFromFailed(Long eventId, String reason, LocalDateTime now) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(now,     "now");

        return queryFactory
                .update(record)
                .set(record.inboxEventRecordStatus, InboxEventRecordStatus.DEAD_LETTER)
                .setNull(record.workerId)
                .setNull(record.leaseUntil)
                .setNull(record.leaseId)
                .set(record.lastError, reason)
                .set(record.updatedAt, now)
                .where(
                        eqEventIdOrFalse(eventId),
                        eqStatusOrFalse(FAILED)
                )
                .execute();
    }

    @Override
    public long markDeadLetter(Long eventId, String leaseId, String reason, LocalDateTime now) {

        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseId, "leaseId");
        Objects.requireNonNull(now, "now");

        return queryFactory
                .update(record)
                .set(record.inboxEventRecordStatus, InboxEventRecordStatus.DEAD_LETTER)
                .set(record.lastError, reason)
                .set(record.updatedAt, now)
                .setNull(record.workerId)
                .setNull(record.leaseUntil)
                .setNull(record.leaseId)
                .where(
                        eqEventIdOrFalse(eventId),
                        eqStatusOrFalse(PROCESSING),
                        eqLeaseIdOrFalse(leaseId)
                )
                .execute();

    }

    private BooleanExpression idInOrFalse(Collection<Long> ids) {
        return (ids == null || ids.isEmpty()) ? Expressions.FALSE : record.id.in(ids);
    }

    private BooleanExpression statusInOrFalse(Collection<InboxEventRecordStatus> statuses) {
        return (statuses == null || statuses.isEmpty()) ? Expressions.FALSE : record.inboxEventRecordStatus.in(statuses);
    }

    private BooleanExpression eqStatusOrFalse(InboxEventRecordStatus status) {
        return (status == null) ? Expressions.FALSE : record.inboxEventRecordStatus.eq(status);
    }

    private BooleanExpression eqLeaseIdOrFalse(String leaseId) {
        return (leaseId == null) ? Expressions.FALSE : record.leaseId.eq(leaseId);
    }

    private BooleanExpression eqEventIdOrFalse(Long eventId) {
        return (eventId == null) ? Expressions.FALSE : record.eventId.eq(eventId);
    }

    private BooleanExpression idInOrNull(Collection<Long> ids) {
        return (ids == null || ids.isEmpty()) ? null : record.id.in(ids);
    }

    private BooleanExpression eqStatusOrNull(InboxEventRecordStatus status) {
        return status == null ? null : record.inboxEventRecordStatus.eq(status);
    }


}
