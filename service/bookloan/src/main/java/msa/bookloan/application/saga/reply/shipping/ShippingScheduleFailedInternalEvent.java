package msa.bookloan.application.saga.reply.shipping;

import msa.common.events.bookloan.saga.reply.shipping.ShippingScheduleFailedReply;
import msa.common.util.IdConverter;

public record ShippingScheduleFailedInternalEvent(
        Long eventId,
        Long sagaId,
        Long causationCommandId,
        Long loanVersion,
        String reasonCode,
        String message
) {

    public static ShippingScheduleFailedInternalEvent from(ShippingScheduleFailedReply reply) {
        Long eventId = IdConverter.parseLongOrThrow(reply.eventId(), "eventId");
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");
        Long causationCommandId = IdConverter.parseLongOrThrow(reply.causationCommandId(), "causationCommandId");

        return new ShippingScheduleFailedInternalEvent(
                eventId,
                sagaId,
                causationCommandId,
                reply.loanVersion(),
                reply.reasonCode(),
                reply.message()
        );
    }
}
