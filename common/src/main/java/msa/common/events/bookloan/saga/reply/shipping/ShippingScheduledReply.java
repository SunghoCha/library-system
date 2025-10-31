package msa.common.events.bookloan.saga.reply.shipping;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

public record ShippingScheduledReply(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long loanVersion,
        Long shipmentId,
        Long bookId,
        String trackingNo   // 선택(없으면 null 허용)
) implements SagaReplyEvent {}
