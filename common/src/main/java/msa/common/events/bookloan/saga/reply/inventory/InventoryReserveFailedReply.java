package msa.common.events.bookloan.saga.reply.inventory;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

public record InventoryReserveFailedReply(
        String eventId,
        String sagaId,
        String causationCommandId,
        Long loanVersion,
        String bookId,
        String reasonCode,
        String message
) implements SagaReplyEvent {}