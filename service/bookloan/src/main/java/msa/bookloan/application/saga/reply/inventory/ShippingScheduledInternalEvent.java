package msa.bookloan.application.saga.reply.inventory;

public record ShippingScheduledInternalEvent(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        ShippingScheduledPayload payload
) {}
