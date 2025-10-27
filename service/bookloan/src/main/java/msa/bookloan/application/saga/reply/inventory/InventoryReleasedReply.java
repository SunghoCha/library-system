package msa.bookloan.application.saga.reply.inventory;

import msa.bookloan.application.saga.reply.SagaReplyEvent;

public record InventoryReleasedReply(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        InventoryReleasedPayload payload
) implements SagaReplyEvent {}
