package msa.bookloan.adapter.in.messaging.kafka.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

@Deprecated
@Component
@RequiredArgsConstructor
public class ReplyPayloadTranslatorV2 {

    private final ObjectMapper objectMapper;

    private final Map<ReplyType, Function<SagaReplyEnvelope, Object>> translators =
            new EnumMap<>(ReplyType.class);
    
    public Object toInternalEvent(SagaReplyEnvelope envelope) {
        ReplyType type = ReplyType.from(envelope.replyType());
        Function<SagaReplyEnvelope, Object> function = translators.get(type);
        if (function == null) {
            throw new IllegalArgumentException("Unknown reply type: " + type);
        }
        return function.apply(envelope);
    }
    
    @PostConstruct
    void init() {
        // MemberChecked
        translators.put(ReplyType.MemberChecked, e ->
                new MemberCheckedInternalEvent(
                        toLong(e.eventId()), e.sagaId(), toLongOrNull(e.causationCommandId()),
                        e.sourceAggregateVersion(),
                        read(e, MemberCheckedPayload.class)
                ));

        // InventoryReserved
        translators.put(ReplyType.InventoryReserved, e ->
                new InventoryReservedInternalEvent(
                        toLong(e.eventId()), e.sagaId(), toLongOrNull(e.causationCommandId()),
                        e.sourceAggregateVersion(),
                        read(e, InventoryReservedPayload.class)
                ));

        // InventoryReserveFailed
        translators.put(ReplyType.InventoryReserveFailed, e ->
                new InventoryReserveFailedInternalEvent(
                        toLong(e.eventId()), e.sagaId(), toLongOrNull(e.causationCommandId()),
                        e.sourceAggregateVersion(),
                        read(e, InventoryReserveFailedPayload.class)
                ));

        // PointCharged
        translators.put(ReplyType.PointCharged, e ->
                new PointChargedInternalEvent(
                        toLong(e.eventId()), e.sagaId(), toLongOrNull(e.causationCommandId()),
                        e.sourceAggregateVersion(),
                        read(e, PointChargedPayload.class)
                ));

        // PointChargeFailed
        translators.put(ReplyType.PointChargeFailed, e ->
                new PointChargeFailedInternalEvent(
                        toLong(e.eventId()), e.sagaId(), toLongOrNull(e.causationCommandId()),
                        e.sourceAggregateVersion(),
                        read(e, PointChargeFailedPayload.class)
                ));

        // ShippingScheduled
        translators.put(ReplyType.ShippingScheduled, e ->
                new ShippingScheduledInternalEvent(
                        toLong(e.eventId()), e.sagaId(), toLongOrNull(e.causationCommandId()),
                        e.sourceAggregateVersion(),
                        read(e, ShippingScheduledPayload.class)
                ));

        // ShippingScheduleFailed
        translators.put(ReplyType.ShippingScheduleFailed, e ->
                new ShippingScheduleFailedInternalEvent(
                        toLong(e.eventId()), e.sagaId(), toLongOrNull(e.causationCommandId()),
                        e.sourceAggregateVersion(),
                        read(e, ShippingScheduleFailedPayload.class)
                ));

        // PointRefunded
        translators.put(ReplyType.PointRefunded, e ->
                new PointRefundedInternalEvent(
                        toLong(e.eventId()), e.sagaId(), toLongOrNull(e.causationCommandId()),
                        e.sourceAggregateVersion(),
                        read(e, PointRefundedPayload.class)
                ));

        // InventoryReleased
        translators.put(ReplyType.InventoryReleased, e ->
                new InventoryReleasedInternalEvent(
                        toLong(e.eventId()), e.sagaId(), toLongOrNull(e.causationCommandId()),
                        e.sourceAggregateVersion(),
                        read(e, InventoryReleasedPayload.class)
                ));
    }

    private <P> P read(SagaReplyEnvelope e, Class<P> type) {
        try {
            return objectMapper.convertValue(e.payload(), type);
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
        return Long.parseLong(s);
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
