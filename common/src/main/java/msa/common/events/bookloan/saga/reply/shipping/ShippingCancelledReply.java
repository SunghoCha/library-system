package msa.common.events.bookloan.saga.reply.shipping;

public record ShippingCancelledReply(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long loanVersion,
        String reasonCode,
        String message
) {
}
