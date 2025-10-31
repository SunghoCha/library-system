package msa.bookcatalog.adapter.out.persistence.outbox.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.QOutboxEventRecord;
import msa.common.events.outbox.OutboxEventRecordStatus;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import static msa.common.events.outbox.OutboxEventRecordStatus.*;

@Repository
@RequiredArgsConstructor
public class OutboxEventRecordRepositoryImpl implements OutboxEventRecordRepositoryCustom {

    private final QOutboxEventRecord r = QOutboxEventRecord.outboxEventRecord;
    private final JPAQueryFactory queryFactory;

    @Override
    public List<OutboxEventRecord> findPublishingByIdsOrderByOccurredAt(
            Collection<Long> ids) {

        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return queryFactory
                .selectFrom(r)
                .where(
                        idIn(ids),
                        eqStatus(PUBLISHING)
                )
                .orderBy(r.occurredAt.asc(), r.id.asc())
                .fetch();
    }

    @Override
    public long tryClaimFromNew(Long eventId, String workerId, String leaseId, LocalDateTime now, int leaseSeconds) {
        if (eventId == null) return 0L;

        LocalDateTime leaseUntil = now.plusSeconds(leaseSeconds);
        return queryFactory
                .update(r)
                .set(r.outboxEventRecordStatus, PUBLISHING)
                .set(r.workerId, workerId)
                .set(r.leaseId, leaseId)
                .set(r.leaseUntil, leaseUntil)
                .set(r.updatedAt, now)
                .where(
                        eqEventId(eventId)
                                .and(eqStatus(NEW))
                )
                .execute();
    }

    // 이미 스킵락으로 잡힌 대상에 대해서만 실행해야함
    // TODO : 내부에서 시간계산하는것도 이상해보임
    @Override
    public long markPublishing(Collection<Long> ids, String workerId, String leaseId,
                                   LocalDateTime now, LocalDateTime leaseUntil) {
        if (ids == null || ids.isEmpty()) return 0L;


        return queryFactory
                .update(r)
                .set(r.outboxEventRecordStatus, PUBLISHING)
                .set(r.workerId, workerId)
                .set(r.leaseUntil, leaseUntil)
                .set(r.leaseId, leaseId)
                .set(r.updatedAt, now)
                .where(r.id.in(ids))
                .execute();

    }

    @Override
    public long markPublished(Collection<Long> ids, String leaseId, LocalDateTime now) {
        if (ids == null || ids.isEmpty()) return 0L;

        return queryFactory
                .update(r)
                .set(r.outboxEventRecordStatus, PUBLISHED)
                .setNull(r.workerId)
                .setNull(r.leaseUntil)
                .set(r.updatedAt, now)
                .where(
                        idIn(ids)
                                .and(eqStatus(PUBLISHING))
                                .and(eqLeaseId(leaseId))
                )
                .execute();
    }

    @Override
    public long markFailed(Collection<Long> ids, String leaseId, String lastError, LocalDateTime now) {
        if (ids == null || ids.isEmpty()) return 0L;

        return queryFactory
                .update(r)
                .set(r.outboxEventRecordStatus, FAILED)
                .set(r.retryCount, r.retryCount.add(1))
                .setNull(r.workerId)
                .setNull(r.leaseUntil)
                .set(r.lastError, lastError)
                .set(r.updatedAt, now)
                .where(
                        idIn(ids)
                                .and(eqStatus(PUBLISHING))
                                .and(eqLeaseId(leaseId))
                )
                .execute();
    }

    @Override
    public long markDeadFromFailed(Long eventId, String reason, LocalDateTime now) {
        if (eventId == null) return 0L;

        return queryFactory
                .update(r)
                .set(r.outboxEventRecordStatus, DEAD_LETTER)
                .setNull(r.workerId)
                .setNull(r.leaseUntil)
                .set(r.lastError, reason)
                .set(r.updatedAt, now)
                .where(
                        eqEventId(eventId)
                                .and(eqStatus(FAILED))
                )
                .execute();
    }

    @Override
    public long markPublishedByEventId(Long eventId, String leaseId, LocalDateTime now) {
        if (eventId == null) return 0L;

        return queryFactory
                .update(r)
                .set(r.outboxEventRecordStatus, PUBLISHED)
                .setNull(r.workerId)
                .setNull(r.leaseId)
                .setNull(r.leaseUntil)
                .set(r.updatedAt, now)
                .where(
                        eqEventId(eventId)
                                .and(eqStatus(PUBLISHING))
                                .and(eqLeaseId(leaseId))
                )
                .execute();
    }

    @Override
    public long markFailedByEventId(Long eventId, String leaseId, String lastError, LocalDateTime now) {
        if (eventId == null) return 0L;

        return queryFactory
                .update(r)
                .set(r.outboxEventRecordStatus, FAILED)
                .set(r.retryCount, r.retryCount.add(1))
                .setNull(r.workerId)
                .setNull(r.leaseId)
                .setNull(r.leaseUntil)
                .set(r.lastError, lastError)
                .set(r.updatedAt, now)
                .where(
                        eqEventId(eventId)
                                .and(eqStatus(PUBLISHING))
                                .and(eqLeaseId(leaseId))
                )
                .execute();
    }

    private BooleanExpression idIn(Collection<Long> ids) {
        return (ids == null || ids.isEmpty())
                ? Expressions.FALSE
                : r.id.in(ids);
    }

    private BooleanExpression eqStatus(OutboxEventRecordStatus status) {
        return status == null ? null : r.outboxEventRecordStatus.eq(status);
    }

    private BooleanExpression eqEventId(Long eventId) {
        return eventId == null ? null : r.eventId.eq(eventId);
    }

    private BooleanExpression eqLeaseId(String leaseId) {
        return leaseId == null ? null : r.leaseId.eq(leaseId);
    }


}
