package msa.bookloan.adapter.in.messaging.kafka.util;

import msa.bookloan.application.saga.reply.SagaReplyEvent;

@FunctionalInterface
interface EventFactory<P, R extends SagaReplyEvent> {
    R create(Long eventId, String sagaId, Long causationId, Long version, P payload);
}
