package msa.bookcatalog.adapter.out.persistence.outbox.repository;

import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRecordRepositoryCustom {

    List<OutboxEventRecord> findEventsToRetryWithSkipLock(
            int maxRetry, int limit, LocalDateTime staleThreshold, LocalDateTime timeoutThreshold);

}
