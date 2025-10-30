package msa.bookloan.adapter.out.persistence.outbox.repository;

import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
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
    @Query("select e.eventId from OutboxEventRecord e where e.eventId in :ids")
    Set<Long> findExistingEventIdsByEventIdIn(Collection<Long> ids);

    Optional<OutboxEventRecord> findByEventId(Long eventId);

    // TODO : 쿼리최적화 필요한지 체크해보기, o.aggregate_type <> 'LoanSaga' 작동하는지 체크
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
                  AND s.status IN ('PROCESSING','COMPENSATING')
            )
          )
        ORDER BY o.occurred_at ASC, o.id ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Long> lockClaimableIdsV1(@Param("limit") int limit,
                                @Param("maxRetry") int maxRetry,
                                @Param("now") LocalDateTime now,
                                @Param("grace") LocalDateTime graceThreshold,
                                @Param("stale") LocalDateTime staleThreshold);

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
            ORDER BY o.occurred_at ASC, o.id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED;
        """, nativeQuery = true)
    List<Long> lockClaimableIds(@Param("limit") int limit,
                                @Param("maxRetry") int maxRetry,
                                @Param("now") LocalDateTime now,
                                @Param("grace") LocalDateTime graceThreshold,
                                @Param("stale") LocalDateTime staleThreshold);


    @Modifying
    @Query(value = """
            INSERT INTO outbox_event_record
                (id, event_id, event_type, aggregate_id, aggregate_type, aggregate_version,
                 payload, status, retry_count, occurred_at, topic, partition_key, created_at, updated_at)
            VALUES (:id, :eventId, :eventType, :aggregateId, :aggregateType, :aggregateVersion,
                    :payload, 'NEW', 0, :occurredAt, :topic, :partitionKey, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE
              updated_at = updated_at
            """, nativeQuery = true)
    int upsertOutbox(@Param("id") long id,
                     @Param("eventId") long eventId,
                     @Param("eventTypeV1") String eventType,
                     @Param("aggregateId") String aggregateId,
                     @Param("aggregateType") String aggregateType,
                     @Param("aggregateVersion") Long aggregateVersion,
                     @Param("payload") String payload,
                     @Param("topic") String topic,
                     @Param("partitionKey") String partitionKey,
                     @Param("occurredAt") LocalDateTime occurredAt);
}

