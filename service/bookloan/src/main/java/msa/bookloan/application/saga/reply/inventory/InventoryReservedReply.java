package msa.bookloan.application.saga.reply.inventory;

import msa.bookloan.application.saga.reply.SagaReplyEvent;

public record InventoryReservedReply(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        InventoryReservedPayload payload
) implements SagaReplyEvent {}
