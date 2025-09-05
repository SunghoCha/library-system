package msa.bookcatalog.infra.outbox.repository;

import msa.common.events.outbox.OutboxEventRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookCatalogOutboxEventRecordRepository extends JpaRepository<BookCatalogOutboxEventRecord, Long>, BookCatalogOutboxEventRecordRepositoryCustom {
    Optional<BookCatalogOutboxEventRecord> findByEventId(Long eventId);
    List<BookCatalogOutboxEventRecord> findByOutboxEventRecordStatusInAndRetryCountLessThan(
            List<OutboxEventRecordStatus> statuses,int retryCount);
}
