package msa.bookloan.adapter.in.messaging.kafka.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import msa.bookloan.application.event.ReplyType;
import msa.bookloan.application.event.SagaReplyEnvelope;
import msa.bookloan.application.saga.reply.SagaReplyEvent;
import msa.bookloan.application.saga.reply.inventory.*;
import msa.bookloan.application.saga.reply.member.MemberCheckedInternalEvent;
import msa.bookloan.application.saga.reply.member.MemberCheckedPayload;
import msa.bookloan.application.saga.reply.point.*;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedPayload;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledPayload;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class ReplyPayloadTranslator {

    private final ObjectMapper objectMapper;

    private final Map<ReplyType, Function<SagaReplyEnvelope, SagaReplyEvent>> translators;

    public SagaReplyEvent toInternalEvent(SagaReplyEnvelope envelope) {
        ReplyType type = ReplyType.from(envelope.replyType());
        Function<SagaReplyEnvelope, SagaReplyEvent> function = translators.get(type);
        if (function == null) {
            throw new IllegalArgumentException("Unknown reply type: " + type);
        }
        return function.apply(envelope);
    }

    public ReplyPayloadTranslator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        EnumMap<ReplyType, Function<SagaReplyEnvelope, SagaReplyEvent>> map = new EnumMap<>(ReplyType.class);

        // MemberChecked
        map.put(ReplyType.MemberChecked,
                createTranslator(MemberCheckedPayload.class, MemberCheckedInternalEvent::new));

        // InventoryReserved
        map.put(ReplyType.InventoryReserved,
                createTranslator(InventoryReservedPayload.class, InventoryReservedInternalEvent::new));

        // InventoryReserveFailed
        map.put(ReplyType.InventoryReserveFailed,
                createTranslator(InventoryReserveFailedPayload.class, InventoryReserveFailedInternalEvent::new));

        // PointCharged
        map.put(ReplyType.PointCharged,
                createTranslator(PointChargedPayload.class, PointChargedInternalEvent::new));

        // PointChargeFailed
        map.put(ReplyType.PointChargeFailed,
                createTranslator(PointChargeFailedPayload.class, PointChargeFailedInternalEvent::new));

        // ShippingScheduled
        map.put(ReplyType.ShippingScheduled,
                createTranslator(ShippingScheduledPayload.class, ShippingScheduledInternalEvent::new));

        // ShippingScheduleFailed
        map.put(ReplyType.ShippingScheduleFailed,
                createTranslator(ShippingScheduleFailedPayload.class, ShippingScheduleFailedInternalEvent::new));

        // PointRefunded
        map.put(ReplyType.PointRefunded,
                createTranslator(PointRefundedPayload.class, PointRefundedInternalEvent::new));

        // InventoryReleased
        map.put(ReplyType.InventoryReleased,
                createTranslator(InventoryReleasedPayload.class, InventoryReleasedInternalEvent::new));

        translators = Collections.unmodifiableMap(map);

    }

    private <P> P read(SagaReplyEnvelope envelope, Class<P> type) {
        if (envelope.payload() == null) {
            throw new IllegalStateException("Reply payload is null for type=" + type);
        }

        try {
            return objectMapper.convertValue(envelope.payload(), type);
        } catch (IllegalArgumentException ex) {
            // dlq 필요할수도
            throw new IllegalStateException("Payload mapping failed to " + type.getSimpleName()
                    + ": " + ex.getMessage(), ex);
        }
    }

    private <P, R extends SagaReplyEvent> Function<SagaReplyEnvelope, R> createTranslator(
            Class<P> payloadType,
            EventFactory<P, R> eventFactory) {

        return e -> eventFactory.create(
                toLong(e.eventId()),
                e.sagaId(),
                toLongOrNull(e.causationCommandId()),
                e.sourceAggregateVersion(),
                read(e, payloadType)
        );
    }

    private static Long toLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid eventId: " + s, ex);
        }
    }

    private static Long toLongOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }
}
