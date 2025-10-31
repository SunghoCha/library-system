package msa.bookloan.application.saga.reply.point;

import msa.common.events.bookloan.saga.reply.point.PointChargeFailedReply;
import msa.common.util.IdConverter;

public record PointChargeFailedInternalEvent(
        Long eventId,
        Long sagaId,
        Long causationCommandId,
        Long loanVersion,
        String reasonCode,
        String message
) {

    public static PointChargeFailedInternalEvent from(PointChargeFailedReply reply) {
        Long eventId = IdConverter.parseLongOrThrow(reply.eventId(), "eventId");
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");
        Long causationCommandId = IdConverter.parseLongOrThrow(reply.causationCommandId(), "causationCommandId");

        return new PointChargeFailedInternalEvent(
                eventId,
                sagaId,
                causationCommandId,
                reply.loanVersion(),
                reply.reasonCode(),
                reply.message()
        );
    }
}
