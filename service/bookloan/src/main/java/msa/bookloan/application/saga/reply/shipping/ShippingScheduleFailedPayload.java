package msa.bookloan.application.saga.reply.shipping;

public record ShippingScheduleFailedPayload(
        String reasonCode,  // e.g. ADDRESS_INVALID, CARRIER_DOWN ...
        String message
) {}
