package msa.bookloan.adapter.in.messaging.kafka.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import msa.bookloan.application.event.ReplyType;
import msa.bookloan.application.event.SagaReplyEnvelope;
import msa.bookloan.application.saga.reply.inventory.*;
import msa.bookloan.application.saga.reply.member.MemberCheckedPayload;
import msa.bookloan.application.saga.reply.member.MemberCheckedReply;
import msa.bookloan.application.saga.reply.point.*;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedReply;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedPayload;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledReply;
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
                return new MemberCheckedReply(
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
                return new InventoryReservedReply(
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
                return new InventoryReserveFailedReply(
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
                return new PointChargedReply(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion, payload);
            }
            case PointChargeFailed: {
                PointChargeFailedPayload payload =
                        objectMapper.convertValue(envelope.payload(), PointChargeFailedPayload.class);
                return new PointChargeFailedReply(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion,
                        payload);
            }
            case ShippingScheduled: {
                ShippingScheduledPayload payload =
                        objectMapper.convertValue(envelope.payload(), ShippingScheduledPayload.class);
                return new ShippingScheduledReply(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion,
                        payload);
            }
            case ShippingScheduleFailed: {
                ShippingScheduleFailedPayload payload =
                        objectMapper.convertValue(envelope.payload(), ShippingScheduleFailedPayload.class);
                return new ShippingScheduleFailedReply(
                        eventId,
                        envelope.sagaId(),
                        causationCommandId,
                        sourceAggregateVersion,
                        payload);
            }
            case PointRefunded: {
                PointRefundedPayload payload =
                        objectMapper.convertValue(envelope.payload(), PointRefundedPayload.class);
                return new PointRefundedReply(
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
                return new InventoryReleasedReply(
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
