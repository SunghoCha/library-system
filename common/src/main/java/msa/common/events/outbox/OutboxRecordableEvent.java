package msa.common.events.outbox;

public interface OutboxRecordableEvent {
    Long eventId();
    java.time.LocalDateTime occurredAt();
}
