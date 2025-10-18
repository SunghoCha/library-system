package msa.common.events.outbox;

import msa.common.events.EventType;

import java.time.LocalDateTime;

public interface OutboxRecordableEvent {
    Long eventId();
    EventType eventType();
    String aggregateType();     // 예: "BookLoan", "LoanSaga"
    String aggregateId();       // 문자열로 통일 (필요하면 구현에서 String.valueOf)
    Long aggregateVersion();
    LocalDateTime occurredAt();
}
