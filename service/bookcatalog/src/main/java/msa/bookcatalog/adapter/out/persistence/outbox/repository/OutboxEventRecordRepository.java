package msa.bookcatalog.adapter.out.persistence.outbox.repository;

import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    @Modifying
    @Query(value = """
            INSERT INTO outbox_event_record
                (id, event_id, event_type, aggregate_id, aggregate_type, aggregate_version,
                 payload, status, occurred_at, topic, partition_key, created_at, updated_at)
            VALUES (:id, :eventId, :eventTypeV1, :aggregateId, :aggregateType, :aggregateVersion,
                    :payload, 'NEW', :occurredAt, :topic, :partitionKey, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE
              updated_at = updated_at
            """, nativeQuery = true)
    int upsertOutbox(@Param("id") long id,
                     @Param("eventId") long eventId,
                     @Param("eventTypeV1") String eventTypeV1,
                     @Param("aggregateId") String aggregateId,
                     @Param("aggregateType") String aggregateType,
                     @Param("aggregateVersion") Long aggregateVersion,
                     @Param("payload") String payload,
                     @Param("topic") String topic,
                     @Param("partitionKey") String partitionKey,
                     @Param("occurredAt") LocalDateTime occurredAt);

}
