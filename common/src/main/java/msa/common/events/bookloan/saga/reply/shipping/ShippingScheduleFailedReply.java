package msa.common.events.bookloan.saga.reply.shipping;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

public record ShippingScheduleFailedReply(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long loanVersion,
        String reasonCode,
        String message
) implements SagaReplyEvent {}
