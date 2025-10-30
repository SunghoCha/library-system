package msa.common.events.bookloan.saga.reply.shipping;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

public record ShippingAcceptedReply(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long loanVersion,
        Long bookId,
        Long provisionalShipmentId, // nullable
        String trackingNoPreview    // nullable
) implements SagaReplyEvent {}
