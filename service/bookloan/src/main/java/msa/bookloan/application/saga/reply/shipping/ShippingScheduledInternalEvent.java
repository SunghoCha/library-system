package msa.bookloan.application.saga.reply.shipping;

import msa.bookloan.application.saga.reply.SagaReplyEvent;

public record ShippingScheduledInternalEvent(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        ShippingScheduledPayload payload
) implements SagaReplyEvent {}
