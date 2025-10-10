package msa.bookloan.application.saga.command;

public record InventoryReserveFailedPayload(
        Long bookId,
        String reasonCode,
        String message
) {}
