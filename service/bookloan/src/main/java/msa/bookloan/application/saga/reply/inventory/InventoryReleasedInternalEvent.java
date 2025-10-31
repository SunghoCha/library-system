package msa.bookloan.application.saga.reply.inventory;

import msa.common.events.bookloan.saga.reply.inventory.InventoryReleasedReply;
import msa.common.util.IdConverter;

public record InventoryReleasedInternalEvent(
        Long eventId,
        Long sagaId,
        Long causationCommandId,
        Long loanVersion,
        Long bookId,
        Long reservationId
) {

    public static InventoryReleasedInternalEvent from(InventoryReleasedReply reply) {
        Long eventId = IdConverter.parseLongOrThrow(reply.eventId(), "eventId");
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");
        Long causationCommandId = IdConverter.parseLongOrThrow(reply.causationCommandId(), "causationCommandId");
        Long bookId = IdConverter.parseLongOrThrow(reply.bookId(), "bookId");
        Long reservationId = IdConverter.parseLongOrThrow(reply.reservationId(), "reservationId");

        return new InventoryReleasedInternalEvent(
                eventId,
                sagaId,
                causationCommandId,
                reply.loanVersion(),
                bookId,
                reservationId
        );
    }
}
