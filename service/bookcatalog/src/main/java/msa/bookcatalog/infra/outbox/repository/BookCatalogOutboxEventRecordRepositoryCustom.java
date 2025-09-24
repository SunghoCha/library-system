package msa.bookcatalog.infra.outbox.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookCatalogOutboxEventRecordRepositoryCustom {

    List<BookCatalogOutboxEventRecord> findEventsToRetryWithSkipLock(
            int maxRetry, int limit, LocalDateTime staleThreshold, LocalDateTime timeoutThreshold);

}
