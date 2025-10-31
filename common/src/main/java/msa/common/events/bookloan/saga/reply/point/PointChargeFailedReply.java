package msa.common.events.bookloan.saga.reply.point;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

public record PointChargeFailedReply(
        String eventId,
        String sagaId,
        String causationCommandId,
        Long loanVersion,
        String reasonCode,
        String message
) implements SagaReplyEvent {}
