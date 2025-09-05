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


}
