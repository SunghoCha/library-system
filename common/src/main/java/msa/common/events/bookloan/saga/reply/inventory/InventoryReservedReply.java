package msa.common.events.bookloan.saga.reply.inventory;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

public record InventoryReservedReply(
        String eventId,
        String sagaId,
        String causationCommandId,
        Long loanVersion,
        String bookId,
        String reservationId
) implements SagaReplyEvent {}
