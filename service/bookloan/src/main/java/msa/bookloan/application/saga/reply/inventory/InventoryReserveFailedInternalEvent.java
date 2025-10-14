package msa.bookloan.application.saga.reply.inventory;

import msa.bookloan.application.saga.reply.SagaReplyEvent;

public record InventoryReserveFailedInternalEvent(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        InventoryReserveFailedPayload payload
) implements SagaReplyEvent {}