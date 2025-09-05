package msa.common.events.outbox;

public enum OutboxEventRecordStatus {
    NEW,
    PUBLISHING,
    PUBLISHED,
    FAILED,
    DEAD_LETTER
}
