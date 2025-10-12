package msa.bookloan.application.saga.reply.member;

public record PointChargedInternalEvent(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        PointChargedPayload payload
) {}
