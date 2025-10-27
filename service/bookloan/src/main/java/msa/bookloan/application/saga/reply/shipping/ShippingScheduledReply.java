package msa.bookloan.application.saga.reply.shipping;

import msa.bookloan.application.saga.reply.SagaReplyEvent;

public record ShippingScheduledReply(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        ShippingScheduledPayload payload
) implements SagaReplyEvent {}
