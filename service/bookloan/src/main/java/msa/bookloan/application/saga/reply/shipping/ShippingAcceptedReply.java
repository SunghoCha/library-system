package msa.bookloan.application.saga.reply.shipping;

import msa.bookloan.application.saga.reply.SagaReplyEvent;

public record ShippingAcceptedReply(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        ShippingAcceptedPayload payload
) implements SagaReplyEvent {}
