package msa.bookcatalog.infra.outbox.repository;

import jakarta.persistence.LockModeType;
import msa.common.events.outbox.OutboxEventRecordStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookCatalogOutboxEventRecordRepository extends JpaRepository<BookCatalogOutboxEventRecord, Long>, BookCatalogOutboxEventRecordRepositoryCustom {
    Optional<BookCatalogOutboxEventRecord> findByEventId(Long eventId);

    // TODO : 더 이상 필요 없을듯
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r " +
            "FROM BookCatalogOutboxEventRecord r " +
            "WHERE " +
            "(r.outboxEventRecordStatus = 'FAILED' AND r.retryCount < : maxRetryCount) OR " +
            "(r.outboxEventRecordStatus = 'NEW' AND r.occurredAt < :createdBefore )")
    List<BookCatalogOutboxEventRecord> findEventsToRetry(
            @Param("createdBefore") LocalDateTime createdBefore,
            @Param("maxRetryCount") int maxRetryCount,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
                    update BookCatalogOutboxEventRecord r
                    set r.outboxEventRecordStatus = :to
                    where r.eventId = :eventId
                    and r.outboxEventRecordStatus = :from
            """)
    int updateStatusIfCurrent(@Param("eventId") Long eventId,
                              @Param("from") OutboxEventRecordStatus from,
                              @Param("to") OutboxEventRecordStatus to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BookCatalogOutboxEventRecord r
            set r.outboxEventRecordStatus = :to,
                r.lastError = :err,
                r.retryCount = r.retryCount + 1
            where r. eventId = : eventId
            and r.outboxEventRecordStatus = :from
            """)
    int failAndIncrementIfCurrent(@Param("eventId") Long eventId,
                                  @Param("from") OutboxEventRecordStatus from,
                                  @Param("to") OutboxEventRecordStatus to,
                                  @Param("err") String err);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
      update BookCatalogOutboxEventRecord r
         set r.outboxEventRecordStatus = :to,
             r.lastError = :err
       where r.eventId = :eventId
         and r.outboxEventRecordStatus in :from
    """)
    int toDeadLetterIfCurrent(@Param("eventId") Long eventId,
                              @Param("from") List<OutboxEventRecordStatus> from,
                              @Param("to")   OutboxEventRecordStatus to,
                              @Param("err")  String err);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BookCatalogOutboxEventRecord r
            set r.outboxEventRecordStatus = msa.common.events.outbox.OutboxEventRecordStatus.PUBLISHING,
                r.pickedAt = :pickedAt
            where r.eventId in :eventIds and r.outboxEventRecordStatus in :claimableStatuses
            """)
    void updateStatusToPublishing(@Param("eventIds") List<Long> eventIds,
                                  @Param("pickedAt") LocalDateTime pickedAt,
                                  @Param("claimableStatuses") List<OutboxEventRecordStatus> claimableStatuses
    );

}
