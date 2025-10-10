package msa.bookloan.adapter.in.messaging.kafka.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import msa.bookloan.application.event.ReplyType;
import msa.bookloan.application.event.SagaReplyEnvelope;
import msa.bookloan.application.saga.command.InventoryReserveFailedPayload;
import msa.bookloan.application.saga.command.InventoryReservedPayload;
import msa.bookloan.application.saga.command.MemberCheckedPayload;
import msa.bookloan.application.saga.reply.InventoryReserveFailedInternalEvent;
import msa.bookloan.application.saga.reply.InventoryReservedInternalEvent;
import msa.bookloan.application.saga.reply.MemberCheckedInternalEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReplyPayloadTranslator {

    private final ObjectMapper objectMapper;

    public Object toInternalEvent(SagaReplyEnvelope envelope) {
        ReplyType replyType = ReplyType.from(envelope.replyType());
        Long eventId = Long.parseLong(envelope.eventId());
        Long causationEventId = toLongOrNull(envelope.causationEventId());
        Long sourceAggregateVersion = envelope.sourceAggregateVersion();

        switch (replyType) {
            case MemberChecked: {
                MemberCheckedPayload payload =
                        objectMapper.convertValue(envelope.payload(), MemberCheckedPayload.class);
                return new MemberCheckedInternalEvent(
                        eventId,
                        envelope.sagaId(),
                        causationEventId,
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
                        causationEventId,
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
                        causationEventId,
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
