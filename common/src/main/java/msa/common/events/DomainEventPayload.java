package msa.common.events;

import java.time.LocalDateTime;

public interface DomainEventPayload {
    String getAggregateId();
    String getAggregateType();
    String getAggregateVersion();
    String getEventId();
    LocalDateTime getOccurredAt();

}
