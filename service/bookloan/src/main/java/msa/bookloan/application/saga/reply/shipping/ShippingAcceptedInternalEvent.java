package msa.bookloan.application.saga.reply.shipping;

import msa.common.events.bookloan.saga.reply.shipping.ShippingAcceptedReply;
import msa.common.util.IdConverter;

public record ShippingAcceptedInternalEvent(
        Long eventId,
        Long sagaId,
        Long causationCommandId,
        Long loanVersion,
        Long bookId,
        Long provisionalShipmentId,   // nullable
        String trackingNoPreview      // nullable
) {

    public static ShippingAcceptedInternalEvent from(ShippingAcceptedReply reply) {
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");

        return new ShippingAcceptedInternalEvent(
                reply.eventId(),
                sagaId,
                reply.causationCommandId(),
                reply.loanVersion(),
                reply.bookId(),
                reply.provisionalShipmentId(),
                reply.trackingNoPreview()
        );
    }
}
