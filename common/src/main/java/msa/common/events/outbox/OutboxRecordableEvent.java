package msa.common.events.outbox;

import java.time.LocalDateTime;

public interface OutboxRecordableEvent {
    Long eventId();
    Long loanId();            // ← OutboxEventRecord.aggregateId 계산에 필요
    Long aggregateVersion();
    LocalDateTime occurredAt();
}
