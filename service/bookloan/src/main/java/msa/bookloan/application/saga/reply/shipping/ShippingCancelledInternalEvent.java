package msa.bookloan.application.saga.reply.shipping;

import msa.common.events.bookloan.saga.reply.shipping.ShippingCancelledReply;
import msa.common.util.IdConverter;

public record ShippingCancelledInternalEvent(
        Long eventId,
        Long sagaId,
        Long causationCommandId,
        Long loanVersion,
        String reasonCode,
        String message
) {

    public static ShippingCancelledInternalEvent from(ShippingCancelledReply reply) {
        Long eventId = IdConverter.parseLongOrThrow(reply.eventId(), "eventId");
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");
        Long causationCommandId = IdConverter.parseLongOrThrow(reply.causationCommandId(), "causationCommandId");

        return new ShippingCancelledInternalEvent(
                eventId,
                sagaId,
                causationCommandId,
                reply.loanVersion(),
                reply.reasonCode(),
                reply.message()
        );
    }
}