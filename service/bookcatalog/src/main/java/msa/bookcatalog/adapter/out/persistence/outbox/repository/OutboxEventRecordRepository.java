package msa.bookcatalog.adapter.out.persistence.outbox.repository;

import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OutboxEventRecordRepository extends JpaRepository<OutboxEventRecord, Long>, OutboxEventRecordRepositoryCustom {
    Optional<OutboxEventRecord> findByEventId(Long eventId);

    @Query("SELECT o.eventId FROM OutboxEventRecord o WHERE o.eventId IN :eventIds")
    Set<Long> findExistingEventIdsByEventIdIn(@Param("eventIds") Set<Long> eventIds);

    @Query(value = """
            SELECT id
            FROM outbox_event_record
            WHERE
              (
                status = 'NEW'
                AND occurred_at < :grace
              )
              OR
              (
                status = 'FAILED'
                AND retry_count < :maxRetry
              )
              OR
              (
                status = 'PUBLISHING'
                AND (lease_until IS NULL OR lease_until < :now OR picked_at < :stale)
              )
            ORDER BY occurred_at ASC, id ASC
            LIMIT :lim
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> lockClaimableIds(
            @Param("lim") int limit,
            @Param("maxRetry") int maxRetry,
            @Param("now") LocalDateTime now,
            @Param("grace") LocalDateTime graceThreshold,
            @Param("stale") LocalDateTime staleThreshold
    );

//    // 2) 상태 마킹
//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Query(value = """
//            UPDATE outbox_event_record
//            SET
//              status = 'PUBLISHING',
//              worker_id = :workerId,
//              picked_at = :now,
//              lease_until = DATE_ADD(:now, INTERVAL :leaseSec SECOND)
//            WHERE id IN (:ids)
//              AND status IN ('NEW','FAILED','PUBLISHING')
//            """, nativeQuery = true)
//    int markPublishing(
//            @Param("ids") Collection<Long> ids,
//            @Param("workerId") String workerId,
//            @Param("now") LocalDateTime now,
//            @Param("leaseSec") int leaseSeconds
//    );

//    // 가져오기 (정렬 포함) (ids가 전부 publishing된게 아닐 수 있어서 사용하면 위험할듯)
//    @Query("SELECT r FROM OutboxEventRecord r WHERE r.id IN :ids ORDER BY r.occurredAt ASC, r.id ASC")
//    List<OutboxEventRecord> findAllByIdInOrderByOccurredAt(@Param("ids") Collection<Long> ids);

//    @Query("""
//    select r
//    from OutboxEventRecord r
//    where r.id in :ids
//      and r.outboxEventRecordStatus = msa.common.events.outbox.OutboxEventRecordStatus.PUBLISHING
//      and r.workerId = :workerId
//    order by r.occurredAt asc, r.id asc
//    """)
//    List<OutboxEventRecord> findPublishingOwnedByWorkerAndIdInOrderByOccurredAt(
//            @Param("workerId") String workerId,
//            @Param("ids") Collection<Long> ids
//    );

    // 성공 마킹
//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Query(value = """
//            UPDATE outbox_event_record
//            SET status = 'PUBLISHED',
//                worker_id = NULL,
//                lease_until = NULL,
//                picked_at = NULL
//            WHERE id IN (:ids)
//              AND status = 'PUBLISHING'
//              AND worker_id = :workerId
//              AND picked_at = :claimedAt
//            """, nativeQuery = true)
//    int markPublished(@Param("ids") Collection<Long> ids,
//                      @Param("workerId") String workerId,
//                      @Param("claimedAt") LocalDateTime claimedAt);

    // 실패 마킹 (+ backoff)
//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Query(value = """
//            UPDATE outbox_event_record
//            SET status = 'FAILED',
//                retry_count = retry_count + 1,
//                worker_id = NULL,
//                lease_until = NULL,
//                picked_at = NULL,
//                last_error = :lastError
//            WHERE id IN (:ids)
//              AND status = 'PUBLISHING'
//              AND worker_id = :workerId
//              AND picked_at = :claimedAt
//            """, nativeQuery = true)
//    int markFailed(
//            @Param("ids") Collection<Long> ids,
//            @Param("workerId") String workerId,
//            @Param("claimedAt") LocalDateTime claimedAt,
//            @Param("lastError") String lastError
//    );

//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Query(value = """
//            UPDATE outbox_event_record
//            SET status = 'DEAD_LETTER',
//                worker_id = NULL,
//                lease_until = NULL,
//                picked_at = NULL,
//                last_error = :reason
//            WHERE event_id = :eventId
//              AND status = 'FAILED'
//            """, nativeQuery = true)
//    int markDeadFromFailed(@Param("eventId") Long eventId,
//                           @Param("reason") String reason);

//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Query(value = """
//              UPDATE outbox_event_record
//              SET status='PUBLISHED', worker_id=NULL, lease_until=NULL, picked_at=NULL
//              WHERE event_id=:eventId
//                AND status='PUBLISHING'
//                AND worker_id=:workerId
//                AND picked_at=:claimedAt
//            """, nativeQuery = true)
//    int markPublishedByEventId(@Param("eventId") Long eventId,
//                               @Param("workerId") String workerId,
//                               @Param("claimedAt") LocalDateTime claimedAt);

//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Query(value = """
//          UPDATE outbox_event_record
//          SET status='FAILED', retry_count=retry_count+1,
//              worker_id=NULL, lease_until=NULL, picked_at=NULL, last_error=:lastError
//          WHERE event_id=:eventId
//            AND status='PUBLISHING'
//            AND worker_id=:workerId
//            AND picked_at=:claimedAt
//        """, nativeQuery = true)
//    int markFailedByEventId(@Param("eventId") Long eventId,
//                            @Param("workerId") String workerId,
//                            @Param("claimedAt") LocalDateTime claimedAt,
//                            @Param("lastError") String lastError);

//    // 퍼블리셔 선점용
//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Query(value = """
//      UPDATE outbox_event_record
//      SET status='PUBLISHING',
//          worker_id=:workerId,
//          picked_at=:now,
//          lease_until=DATE_ADD(:now, INTERVAL :leaseSec SECOND)
//      WHERE event_id=:eventId AND status='NEW'
//      """, nativeQuery = true)
//    int tryClaimFromNew(@Param("eventId") Long eventId,
//                        @Param("workerId") String workerId,
//                        @Param("now") LocalDateTime now,
//                        @Param("leaseSec") int leaseSeconds);


}
