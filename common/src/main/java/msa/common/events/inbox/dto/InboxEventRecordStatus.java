package msa.common.events.inbox.dto;

public enum InboxEventRecordStatus {
    NEW,
    PROCESSING, // 임시
    PROCESSED,
    FAILED, // 임시
    DEAD_LETTER
}
