package msa.bookloan.infra.inbox.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Repository
@Transactional
@RequiredArgsConstructor
public class BookCatalogProjectionEventRecordRepositoryImpl
        implements BookCatalogProjectionInboxEventRecordRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QBookCatalogProjectionInboxEventRecord record =
            QBookCatalogProjectionInboxEventRecord.bookCatalogProjectionInboxEventRecord;

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
