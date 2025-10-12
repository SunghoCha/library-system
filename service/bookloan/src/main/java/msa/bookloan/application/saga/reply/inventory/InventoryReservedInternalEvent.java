package msa.bookloan.application.saga.reply.inventory;

public record InventoryReservedInternalEvent(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        InventoryReservedPayload payload
) {}
