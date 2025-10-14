package msa.bookloan.application.saga.reply.point;

import msa.bookloan.application.saga.reply.SagaReplyEvent;

public record PointRefundedInternalEvent(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        PointRefundedPayload payload
) implements SagaReplyEvent {}
