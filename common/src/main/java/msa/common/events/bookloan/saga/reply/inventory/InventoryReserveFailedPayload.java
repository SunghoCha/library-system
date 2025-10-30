package msa.common.events.bookloan.saga.reply.inventory;

@Deprecated
public record InventoryReserveFailedPayload(
        Long bookId,
        String reasonCode,
        String message
) {}
