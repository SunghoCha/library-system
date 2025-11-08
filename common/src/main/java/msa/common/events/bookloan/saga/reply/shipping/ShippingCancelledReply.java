package msa.common.events.bookloan.saga.reply.shipping;

public record ShippingCancelledReply(
        String eventId,
        String sagaId,
        String causationCommandId,
        Long loanVersion,
        String reasonCode,
        String message
) {
}
