package msa.bookloan.application.saga.reply;

import msa.bookloan.application.saga.command.InventoryReserveFailedPayload;

public record InventoryReserveFailedInternalEvent(
        Long eventId,
        String sagaId,
        Long causationEventId,
        Long sourceAggregateVersion,
        InventoryReserveFailedPayload payload
) {}