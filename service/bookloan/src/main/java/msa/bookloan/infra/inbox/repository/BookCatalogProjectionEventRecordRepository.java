package msa.bookloan.infra.inbox.repository;

import msa.common.events.inbox.dto.InboxEventRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookCatalogProjectionEventRecordRepository extends JpaRepository<BookCatalogProjectionInboxEventRecord, Long>, BookCatalogProjectionInboxEventRecordRepositoryCustom {
    boolean existsByEventId(Long eventId);

    Optional<BookCatalogProjectionInboxEventRecord> findByEventId(Long eventId);

    List<BookCatalogProjectionInboxEventRecord> findByInboxEventRecordStatusInAndRetryCountLessThan(List<InboxEventRecordStatus> statuses, int retryCount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO book_catalog_inbox
              (id, event_id, aggregate_id, aggregate_version, event_type, payload, inbox_event_record_status,
               first_seen_at, last_seen_at, seen_count, retry_count,
               topic, partition_no, record_offset, last_error, failure_category,
               created_at, updated_at)
            VALUES
              (:id, :eventId, :aggregateId, :aggregateVersion, :eventType, :payload, 'NEW',
               NOW(6), NOW(6), 1, 0,
               :topic, :partitionNo, :recordOffset, NULL, NULL,
               NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE
              seen_count   = seen_count + 1,
              last_seen_at = NOW(6),
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO book_catalog_inbox
              (id, event_id, aggregate_id, aggregate_version, event_type, payload, inbox_event_record_status,
               first_seen_at, last_seen_at, seen_count, retry_count,
               topic, partition_no, record_offset, last_error, failure_category,
               created_at, updated_at)
            VALUES
              (:id, :eventId, :aggregateId, :aggregateVersion, :eventType, NULL, 'DEAD_LETTER',
               NOW(6), NOW(6), 1, 0,
               :topic, :partitionNo, :recordOffset, :lastError, :failureCategory,
               NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE 
              inbox_event_record_status = 
                CASE
                WHEN inbox_event_record_status = 'PROCESSED' // 살짝 과한 느낌도 있지만 이미 처리된 레코드 상태 유지용
                THEN 'PROCESSED'
                ELSE 'DEAD_LETTER'
                END,
              last_error          = :lastError,
              failure_category    = :failureCategory,
              seen_count          = seen_count + 1,
              last_seen_at        = NOW(6),
              updated_at          = NOW(6)
            """, nativeQuery = true)
    int upsertDeadLetter(@Param("id") long id,
                         @Param("eventId") long eventId,
                         @Param("aggregateId") long aggregateId,
                         @Param("aggregateVersion") long aggregateVersion,
                         @Param("eventType") String eventType,
                         @Param("topic") String topic,
                         @Param("partitionNo") int partitionNo,
                         @Param("recordOffset") long recordOffset,
                         @Param("lastError") String lastError,
                         @Param("failureCategory") String failureCategory);

}
