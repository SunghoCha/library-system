package msa.bookloan.adapter.out.persistence.outbox.repository;

import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface OutboxEventRecordRepository extends JpaRepository<OutboxEventRecord, Long> {
    @Query("select e.eventId from OutboxEventRecord e where e.eventId in :ids")
    Set<Long> findExistingEventIdsByEventIdIn(Collection<Long> ids);

    // TODO : 쿼리최적화 필요한지 체크해보기
    @Query(value = """
        SELECT o.id
        FROM outbox_event_record o
        WHERE
          (
            (o.status = 'NEW'        AND o.occurred_at < :grace)
            OR
            (o.status = 'FAILED'     AND o.retry_count < :maxRetry)
            OR
            (o.status = 'PUBLISHING' AND (o.lease_until IS NULL OR o.lease_until < :now OR o.picked_at < :stale))
          )
          AND (
            o.aggregate_type <> 'LoanSaga'
            OR EXISTS (
                SELECT 1
                FROM loan_saga s
                WHERE s.saga_id = o.aggregate_id
                  AND s.status  = 'PROCESSING'
            )
          )
        ORDER BY o.occurred_at ASC, o.id ASC
        LIMIT :lim
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Long> lockClaimableIds(@Param("lim") int limit,
                                @Param("maxRetry") int maxRetry,
                                @Param("now") LocalDateTime now,
                                @Param("grace") LocalDateTime graceThreshold,
                                @Param("stale") LocalDateTime staleThreshold);
}

