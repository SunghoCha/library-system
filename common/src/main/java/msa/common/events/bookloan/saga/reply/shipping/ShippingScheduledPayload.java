package msa.common.events.bookloan.saga.reply.shipping;

@Deprecated
public record ShippingScheduledPayload(
        Long shipmentId,
        Long bookId,
        String trackingNo   // 선택(없으면 null 허용)
) {}