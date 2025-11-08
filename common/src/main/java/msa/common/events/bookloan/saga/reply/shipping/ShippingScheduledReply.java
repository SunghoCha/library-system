package msa.common.events.bookloan.saga.reply.shipping;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

public record ShippingScheduledReply(
        String eventId,
        String sagaId,
        String causationCommandId,
        Long loanVersion,
        String shipmentId,
        String bookId,
        String trackingNo   // 선택(없으면 null 허용)
) implements SagaReplyEvent {}
