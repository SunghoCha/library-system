package msa.common.events.inbox;

public interface InboxRecordableEvent {
    String getEventId();
    String getAggregateId();
    Long getAggregateVersion();
    String getEventType();
}
