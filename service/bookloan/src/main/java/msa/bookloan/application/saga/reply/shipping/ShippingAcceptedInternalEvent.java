package msa.bookloan.application.saga.reply.shipping;

import msa.common.events.bookloan.saga.reply.shipping.ShippingAcceptedReply;
import msa.common.util.IdConverter;

public record ShippingAcceptedInternalEvent(
        Long eventId,
        Long sagaId,
        Long causationCommandId,
        Long loanVersion,
        Long bookId,
        Long provisionalShipmentId,
        String trackingNoPreview
) {

    public static ShippingAcceptedInternalEvent from(ShippingAcceptedReply reply) {
        Long eventId = IdConverter.parseLongOrThrow(reply.eventId(), "eventId");
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");
        Long causationCommandId = IdConverter.parseLongOrThrow(reply.causationCommandId(), "causationCommandId");
        Long bookId = IdConverter.parseLongOrThrow(reply.bookId(), "bookId");
        Long provisionalShipmentId = IdConverter.parseLongOrThrow(reply.provisionalShipmentId(), "provisionalShipmentId");

        return new ShippingAcceptedInternalEvent(
                eventId,
                sagaId,
                causationCommandId,
                reply.loanVersion(),
                bookId,
                provisionalShipmentId,
                reply.trackingNoPreview()
        );
    }
}
