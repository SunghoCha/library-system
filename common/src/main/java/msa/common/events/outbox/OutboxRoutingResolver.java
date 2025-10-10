package msa.common.events.outbox;

import msa.common.events.outbox.dto.OutboxRouting;

public interface OutboxRoutingResolver<T> {
    OutboxRouting resolve(T event);
}
