package msa.common.events.bookloan.saga.reply.inventory;

@Deprecated
public record InventoryReleasedPayload(
        Long bookId,
        Long reservationId     // 예약 해제된 예약 ID (InventoryReservedPayload.reservationId와 짝)
) {}