package msa.bookloan.adapter.in.messaging.kafka.util;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

@FunctionalInterface
interface EventFactory<P, R extends SagaReplyEvent> {
    R create(Long eventId, String sagaId, Long causationCommandId, Long sourceVersion, P payload);
}
