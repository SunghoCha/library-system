package msa.bookcatalog.infra.messaging.outbox.repository;

import msa.bookcatalog.infra.messaging.outbox.entity.OutboxEventRecord;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRecordRepositoryCustom {

    List<OutboxEventRecord> findEventsToRetryWithSkipLock(
            int maxRetry, int limit, LocalDateTime staleThreshold, LocalDateTime timeoutThreshold);

}
