package msa.common.events.bookloan.saga.reply.shipping;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

public record ShippingScheduleFailedReply(
        String eventId,
        String sagaId,
        String causationCommandId,
        Long loanVersion,
        String reasonCode,
        String message
) implements SagaReplyEvent {}
