package msa.bookloan.application.saga.reply.inventory;

public record ShippingScheduleFailedInternalEvent(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        ShippingScheduleFailedPayload payload
) {}
