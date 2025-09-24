package msa.bookcatalog.infra.outbox.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static msa.common.events.outbox.OutboxEventRecordStatus.*;

@Repository
@RequiredArgsConstructor
public class BookCatalogOutboxEventRecordRepositoryImpl implements BookCatalogOutboxEventRecordRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QBookCatalogOutboxEventRecord record =
            QBookCatalogOutboxEventRecord.bookCatalogOutboxEventRecord;

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
                .setHint("jakarta.persistence.lock.timeout", 0)
                .fetch();
    }


}
