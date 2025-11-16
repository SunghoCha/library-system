package msa.common.events.outbox;

import msa.common.events.EventType;
import msa.common.events.EventTypeV1;

import java.time.LocalDateTime;

public interface OutboxRecordableEvent {
    Long eventId();
    EventType eventType();
    String aggregateType();     // 예: "BookLoan", "LoanSaga"
    Long aggregateId();       //
    Long aggregateVersion();
    LocalDateTime occurredAt();
}
