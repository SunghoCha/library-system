package msa.bookcatalog.adapter.out.persistence.outbox.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.QOutboxEventRecord;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static msa.common.events.outbox.OutboxEventRecordStatus.*;

@Repository
@RequiredArgsConstructor
public class OutboxEventRecordRepositoryImpl implements OutboxEventRecordRepositoryCustom {

    private final QOutboxEventRecord record = QOutboxEventRecord.outboxEventRecord;
    private final JPAQueryFactory queryFactory;

    @Override
    public List<OutboxEventRecord> findEventsToRetryWithSkipLock(
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
