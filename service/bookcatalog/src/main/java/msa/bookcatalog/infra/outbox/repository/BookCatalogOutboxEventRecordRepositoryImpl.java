package msa.bookcatalog.infra.outbox.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import msa.common.events.outbox.OutboxEventRecordStatus;
import org.hibernate.LockOptions;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static msa.common.events.outbox.OutboxEventRecordStatus.*;

@Repository
@RequiredArgsConstructor
public class BookCatalogOutboxEventRecordRepositoryImpl implements BookCatalogOutboxEventRecordRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QBookCatalogOutboxEventRecord record =
            QBookCatalogOutboxEventRecord.bookCatalogOutboxEventRecord;

    @Override
    public Long updateStatus(Long eventId, OutboxEventRecordStatus newStatus, Collection<OutboxEventRecordStatus> oldStatuses) {
        return queryFactory
                .update(record)
                .set(record.outboxEventRecordStatus, newStatus)
                .where(
                        record.eventId.eq(eventId),
                        record.outboxEventRecordStatus.in(oldStatuses)
                )
                .execute();
    }

    @Override
    public Long markAsFailed(Long eventId, String errorMessage) {
        return queryFactory
                .update(record)
                .set(record.outboxEventRecordStatus, FAILED)
                .set(record.lastError, errorMessage)
                .where(
                        record.eventId.eq(eventId),
                        record.outboxEventRecordStatus.in(NEW, FAILED)
                )
                .execute();
    }

    @Override
    public Long markAsDeadLetter(Long eventId, String errorMessage) {
        return queryFactory
                .update(record)
                .set(record.outboxEventRecordStatus, DEAD_LETTER)
                .set(record.lastError, errorMessage)
                .where(
                        record.eventId.eq(eventId),
                        record.outboxEventRecordStatus.in(NEW, FAILED)
                )
                .execute();
    }

    @Override
    public List<BookCatalogOutboxEventRecord> findEventsToRetryWithSkipLock(
            int maxRetry,
            int limit,
            LocalDateTime staleThreshold,
            LocalDateTime gracePeriodThreshold
    ) {

        return queryFactory
                .selectFrom(record)
                .where(
                        (record.outboxEventRecordStatus.eq(NEW).and(record.occurredAt.before(gracePeriodThreshold)))
                        .or(record.outboxEventRecordStatus.eq(FAILED).and(record.retryCount.lt((maxRetry))))
                        .or(record.outboxEventRecordStatus.eq(PUBLISHING).and(record.pickedAt.before(staleThreshold)))
                )
                .orderBy(record.occurredAt.asc())
                .limit(limit)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("javax.persistence.lock.timeout", 0L)
                .fetch();
    }


}
