package msa.bookloan.application.saga.reply;

import msa.bookloan.application.saga.command.InventoryReservedPayload;

public record InventoryReservedInternalEvent(
        Long eventId,
        String sagaId,
        Long causationEventId,
        Long sourceAggregateVersion,
        InventoryReservedPayload payload
) {}
