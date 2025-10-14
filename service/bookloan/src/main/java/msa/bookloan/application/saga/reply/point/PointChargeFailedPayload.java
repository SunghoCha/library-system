package msa.bookloan.application.saga.reply.point;

public record PointChargeFailedPayload(
        String reasonCode,
        String message
) {}
