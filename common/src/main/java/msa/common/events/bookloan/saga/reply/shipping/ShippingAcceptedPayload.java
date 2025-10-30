package msa.common.events.bookloan.saga.reply.shipping;

@Deprecated
public record ShippingAcceptedPayload(
        Long bookId,
        Long provisionalShipmentId, // nullable
        String trackingNoPreview    // nullable
) {}
