package msa.common.events.bookloan.saga.reply.point;

@Deprecated
public record PointChargeFailedPayload(
        String reasonCode,
        String message
) {}
