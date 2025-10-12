package msa.bookloan.application.saga.reply.member;

public record PointChargeFailedPayload(
        String reasonCode,
        String message
) {}
