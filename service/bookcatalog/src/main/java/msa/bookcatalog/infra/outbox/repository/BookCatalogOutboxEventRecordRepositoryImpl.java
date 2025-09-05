package msa.bookcatalog.infra.outbox.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.events.outbox.OutboxEventRecordStatus;
import org.springframework.stereotype.Repository;

import java.util.Collection;

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

}
