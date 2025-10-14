package msa.common.events.outbox;

import msa.common.events.outbox.dto.OutboxRouting;

public interface OutboxRoutingResolver<T> {

    default boolean supports(Object payload) {
        return payload != null && payloadType().isInstance(payload);
    }

    default OutboxRouting resolve(Object payload) {
        return doResolve(payloadType().cast(payload));
    }

    Class<T> payloadType();
    OutboxRouting doResolve(T event);


}
