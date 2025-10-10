package msa.bookloan.application.saga.command;

public record InventoryReservedPayload(
        Long bookId,
        Long reservationId
) {}
