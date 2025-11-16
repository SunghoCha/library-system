package msa.inventory.adaptor.out.persistence.outbox.repository;

import msa.inventory.adaptor.out.persistence.outbox.entity.OutboxEventRecord;
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

    @Query(value = """
            SELECT o.id
            FROM outbox_event_record o
            WHERE
              (
                (o.status = 'NEW')
                OR
                (o.status = 'FAILED' AND o.retry_count < :maxRetry)
                OR
                (o.status = 'PUBLISHING' AND (o.lease_until IS NULL OR o.lease_until < :now))
              )
            ORDER BY o.occurred_at ASC, o.id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED;
        """, nativeQuery = true)
    List<Long> lockClaimableIds(@Param("limit") int limit,
                                @Param("maxRetry") int maxRetry,
                                @Param("now") LocalDateTime now);


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
    int upsertOutbox(@Param("id") Long id,
                     @Param("eventId") Long eventId,
                     @Param("eventType") String eventType,
                     @Param("aggregateId") Long aggregateId,
                     @Param("aggregateType") String aggregateType,
                     @Param("aggregateVersion") Long aggregateVersion,
                     @Param("payload") String payload,
                     @Param("topic") String topic,
                     @Param("partitionKey") String partitionKey,
                     @Param("occurredAt") LocalDateTime occurredAt);
}

