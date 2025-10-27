package msa.bookloan.application.saga.reply.point;

import msa.bookloan.application.saga.reply.SagaReplyEvent;

public record PointChargedReply(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        PointChargedPayload payload
) implements SagaReplyEvent {}
