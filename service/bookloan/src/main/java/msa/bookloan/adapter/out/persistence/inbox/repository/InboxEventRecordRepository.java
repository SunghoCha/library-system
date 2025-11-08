package msa.bookloan.adapter.out.persistence.inbox.repository;

import msa.bookloan.adapter.out.persistence.inbox.entity.InboxEventRecord;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InboxEventRecordRepository extends JpaRepository<InboxEventRecord, Long>, InboxEventRecordRepositoryCustom {

    Optional<InboxEventRecord> findByEventId(Long eventId);

    List<InboxEventRecord> findByInboxEventRecordStatusInAndRetryCountLessThan(List<InboxEventRecordStatus> statuses, int retryCount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO inbox_event
              (id, event_id, aggregate_id, aggregate_version, event_type, payload,
               status, seen_count, retry_count,
               topic, partition_no, record_offset, last_error, failure_category,
               created_at, updated_at)
            VALUES
              (:id, :eventId, :aggregateId, :aggregateVersion, :eventType, :payload,
               'NEW', 1, 0,
               :topic, :partitionNo, :recordOffset, NULL, NULL,
               NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE
              seen_count   = seen_count + 1,
              updated_at   = NOW(6)
            """, nativeQuery = true)
    int upsertInbox(@Param("id") long id,
                    @Param("eventId") long eventId,
                    @Param("aggregateId") long aggregateId,
                    @Param("aggregateVersion") long aggregateVersion,
                    @Param("eventType") String eventType,
                    @Param("payload") String payload,
                    @Param("topic") String topic,
                    @Param("partitionNo") int partitionNo,
                    @Param("recordOffset") long recordOffset);

    @Query(value = """
    SELECT id
    FROM inbox_event
    WHERE
          status = 'NEW'
       OR (status = 'FAILED' AND retry_count < :maxRetry)
       OR (status = 'PROCESSING'
           AND (lease_until IS NULL OR lease_until < :now))
    ORDER BY created_at ASC, id ASC
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<Long> lockClaimableInboxIds(
            @Param("limit") int limit,
            @Param("maxRetry") int maxRetry,
            @Param("now") LocalDateTime now);






}
