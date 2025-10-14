package msa.bookloan.adapter.in.messaging.kafka.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import msa.bookloan.application.event.ReplyType;
import msa.bookloan.application.event.SagaReplyEnvelope;
import msa.bookloan.application.saga.reply.inventory.*;
import msa.bookloan.application.saga.reply.member.MemberCheckedPayload;
import msa.bookloan.application.saga.reply.member.MemberCheckedInternalEvent;
import msa.bookloan.application.saga.reply.point.*;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedPayload;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledPayload;
import org.springframework.stereotype.Component;

@Deprecated
@Component
@RequiredArgsConstructor
public class ReplyPayloadTranslatorV1 {

    private final ObjectMapper objectMapper;

    // TODO : 나중에 리팩토링 필요할 듯. case문 반복되고 있음
    public Object toInternalEvent(SagaReplyEnvelope envelope) {
        ReplyType replyType = ReplyType.from(envelope.replyType());
        Long eventId = Long.parseLong(envelope.eventId());
        Long causationCommandId = toLongOrNull(envelope.causationCommandId());
        Long sourceAggregateVersion = envelope.sourceAggregateVersion();

        switch (replyType) {
            case MemberChecked: {
                MemberCheckedPayload payload =
                        objectMapper.convertValue(envelope.payload(), MemberCheckedPayload.class);
                return new MemberCheckedInternalEvent(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion,
                        payload
                );
            }
            case InventoryReserved: {
                InventoryReservedPayload payload =
                        objectMapper.convertValue(envelope.payload(), InventoryReservedPayload.class);
                return new InventoryReservedInternalEvent(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion,
                        payload
                );
            }
            case InventoryReserveFailed: {
                InventoryReserveFailedPayload payload =
                        objectMapper.convertValue(envelope.payload(), InventoryReserveFailedPayload.class);
                return new InventoryReserveFailedInternalEvent(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion,
                        payload
                );
            }
            case PointCharged: {
                PointChargedPayload payload =
                        objectMapper.convertValue(envelope.payload(), PointChargedPayload.class);
                return new PointChargedInternalEvent(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion, payload);
            }
            case PointChargeFailed: {
                PointChargeFailedPayload payload =
                        objectMapper.convertValue(envelope.payload(), PointChargeFailedPayload.class);
                return new PointChargeFailedInternalEvent(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion,
                        payload);
            }
            case ShippingScheduled: {
                ShippingScheduledPayload payload =
                        objectMapper.convertValue(envelope.payload(), ShippingScheduledPayload.class);
                return new ShippingScheduledInternalEvent(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion,
                        payload);
            }
            case ShippingScheduleFailed: {
                ShippingScheduleFailedPayload payload =
                        objectMapper.convertValue(envelope.payload(), ShippingScheduleFailedPayload.class);
                return new ShippingScheduleFailedInternalEvent(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion,
                        payload);
            }
            case PointRefunded: {
                PointRefundedPayload payload =
                        objectMapper.convertValue(envelope.payload(), PointRefundedPayload.class);
                return new PointRefundedInternalEvent(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion,
                        payload
                );
            }
            case InventoryReleased: {
                InventoryReleasedPayload payload =
                        objectMapper.convertValue(envelope.payload(), InventoryReleasedPayload.class);
                return new InventoryReleasedInternalEvent(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion,
                        payload
                );
            }
            default:
                throw new IllegalArgumentException("Unknown reply type: " + replyType);
        }
    }

    private Long toLongOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }
}
