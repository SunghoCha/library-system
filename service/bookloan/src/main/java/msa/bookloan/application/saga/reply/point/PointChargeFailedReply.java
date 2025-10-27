package msa.bookloan.application.saga.reply.point;

import msa.bookloan.application.saga.reply.SagaReplyEvent;

public record PointChargeFailedReply(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        PointChargeFailedPayload payload
) implements SagaReplyEvent {}
