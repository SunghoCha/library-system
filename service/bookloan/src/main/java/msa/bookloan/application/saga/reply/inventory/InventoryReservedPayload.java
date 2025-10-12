package msa.bookloan.application.saga.reply.inventory;

public record InventoryReservedPayload(
        Long bookId,
        Long reservationId
) {}
