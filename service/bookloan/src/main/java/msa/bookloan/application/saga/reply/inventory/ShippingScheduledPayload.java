package msa.bookloan.application.saga.reply.inventory;

public record ShippingScheduledPayload(
        Long shipmentId,
        Long bookId,
        String trackingNo   // 선택(없으면 null 허용)
) {}