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
        String trackingNo
) {

    public static ShippingScheduledInternalEvent from(ShippingScheduledReply reply) {
        Long eventId = IdConverter.parseLongOrThrow(reply.eventId(), "eventId");
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");
        Long causationCommandId = IdConverter.parseLongOrThrow(reply.causationCommandId(), "causationCommandId");
        Long shipmentId = IdConverter.parseLongOrThrow(reply.shipmentId(), "shipmentId");
        Long bookId = IdConverter.parseLongOrThrow(reply.bookId(), "bookId");

        return new ShippingScheduledInternalEvent(
                eventId,
                sagaId,
                causationCommandId,
                reply.loanVersion(),
                shipmentId,
                bookId,
                reply.trackingNo()
        );
    }
}
