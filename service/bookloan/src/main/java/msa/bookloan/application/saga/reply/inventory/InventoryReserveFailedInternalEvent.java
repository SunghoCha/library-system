package msa.bookloan.application.saga.reply.inventory;

import msa.common.events.bookloan.saga.reply.inventory.InventoryReserveFailedReply;
import msa.common.util.IdConverter;

public record InventoryReserveFailedInternalEvent(
        Long eventId,
        Long sagaId,
        Long causationCommandId,
        Long loanVersion,
        Long bookId,
        String reasonCode,
        String message
) {

    public static InventoryReserveFailedInternalEvent from(InventoryReserveFailedReply reply) {
        Long eventId = IdConverter.parseLongOrThrow(reply.eventId(), "eventId");
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");
        Long causationCommandId = IdConverter.parseLongOrThrow(reply.causationCommandId(), "causationCommandId");
        Long bookId = IdConverter.parseLongOrThrow(reply.bookId(), "bookId");

        return new InventoryReserveFailedInternalEvent(
                eventId,
                sagaId,
                causationCommandId,
                reply.loanVersion(),
                bookId,
                reply.reasonCode(),
                reply.message()
        );
    }
}
