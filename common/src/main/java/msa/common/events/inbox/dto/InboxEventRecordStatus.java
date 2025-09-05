package msa.common.events.inbox.dto;

public enum InboxEventRecordStatus {
    NEW,
    PROCESSING,
    PROCESSED,
    FAILED,
    DEAD_LETTER
}
