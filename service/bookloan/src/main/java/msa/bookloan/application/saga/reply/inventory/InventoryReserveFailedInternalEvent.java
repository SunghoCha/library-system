package msa.bookloan.application.saga.reply.inventory;

public record InventoryReserveFailedInternalEvent(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        InventoryReserveFailedPayload payload
) {}