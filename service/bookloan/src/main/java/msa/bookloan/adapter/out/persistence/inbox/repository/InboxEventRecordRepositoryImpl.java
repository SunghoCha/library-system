package msa.bookloan.adapter.out.persistence.inbox.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.persistence.inbox.entity.QInboxEventRecord;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Repository
@Transactional
@RequiredArgsConstructor
public class InboxEventRecordRepositoryImpl
        implements InboxEventRecordRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QInboxEventRecord record = QInboxEventRecord.inboxEventRecord;

    @Override
    public Long updateStatusIfPending(Long eventId, InboxEventRecordStatus newStatus, Collection<InboxEventRecordStatus> oldStatuses) {
        return queryFactory
                .update(record)
                .set(record.inboxEventRecordStatus, newStatus)
                .where(
                        record.eventId.eq(eventId),
                        record.inboxEventRecordStatus.in(oldStatuses)
                )
                .execute();
    }

    public Long incrementRetryCountIfBelowMax(Long eventId, int maxRetryCount, String lastError) {
        return queryFactory
                .update(record)
                .set(record.retryCount, record.retryCount.add(1))
                .set(record.lastError, lastError)
                .where(
                        record.eventId.eq(eventId),
                        record.retryCount.lt(maxRetryCount)
                )
                .execute();
    }
}
