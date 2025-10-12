package msa.bookloan.application.saga.reply.member;

public record PointChargeFailedInternalEvent(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        PointChargeFailedPayload payload
) {}
