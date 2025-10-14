package msa.bookloan.application.saga.reply.inventory;

public record InventoryReserveFailedPayload(
        Long bookId,
        String reasonCode,
        String message
) {}
