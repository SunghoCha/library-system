package msa.common.events.bookloan.saga.reply.inventory;

@Deprecated
public record InventoryReservedPayload(
        Long bookId,
        Long reservationId
) {}
