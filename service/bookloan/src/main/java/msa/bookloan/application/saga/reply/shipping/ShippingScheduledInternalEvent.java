package msa.bookloan.application.saga.reply.shipping;

import msa.common.events.bookloan.saga.reply.shipping.ShippingScheduledReply;
import msa.common.util.IdConverter;

public record ShippingScheduledInternalEvent(
        Long eventId,
        Long sagaId,
        Long causationCommandId,
        Long loanVersion,
        Long shipmentId,
        Long bookId,
        String trackingNo    // nullable
) {

    public static ShippingScheduledInternalEvent from(ShippingScheduledReply reply) {
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");

        return new ShippingScheduledInternalEvent(
                reply.eventId(),
                sagaId,
                reply.causationCommandId(),
                reply.loanVersion(),
                reply.shipmentId(),
                reply.bookId(),
                reply.trackingNo()
        );
    }
}
