package msa.common.events.bookloan.saga.reply.shipping;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

public record ShippingAcceptedReply(
        String eventId,
        String sagaId,
        String causationCommandId,
        Long loanVersion,
        String bookId,
        String provisionalShipmentId, // nullable
        String trackingNoPreview    // nullable
) implements SagaReplyEvent {}
