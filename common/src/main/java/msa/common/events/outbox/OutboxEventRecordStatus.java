package msa.common.events.outbox;

import java.util.List;

public enum OutboxEventRecordStatus {
    NEW,
    PUBLISHING,
    PUBLISHED,
    FAILED,
    DEAD_LETTER;

    public static final List<OutboxEventRecordStatus> CLAIMABLE_STATUSES = List.of(NEW, FAILED);
}
