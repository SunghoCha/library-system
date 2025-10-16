package msa.bookloan.application.saga.reply.shipping;

public record ShippingAcceptedPayload(
        Long bookId,
        Long provisionalShipmentId, // nullable
        String trackingNoPreview    // nullable
) {}
