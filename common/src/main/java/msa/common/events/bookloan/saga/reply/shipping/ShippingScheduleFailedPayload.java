package msa.common.events.bookloan.saga.reply.shipping;

@Deprecated
public record ShippingScheduleFailedPayload(
        String reasonCode,
        String message
) {}
