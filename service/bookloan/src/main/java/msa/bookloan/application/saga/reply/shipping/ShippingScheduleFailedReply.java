package msa.bookloan.application.saga.reply.shipping;

import msa.bookloan.application.saga.reply.SagaReplyEvent;

public record ShippingScheduleFailedReply(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        ShippingScheduleFailedPayload payload
) implements SagaReplyEvent {}
