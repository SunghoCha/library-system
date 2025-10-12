package msa.bookloan.adapter.in.messaging.kafka.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import msa.bookloan.application.event.ReplyType;
import msa.bookloan.application.event.SagaReplyEnvelope;
import msa.bookloan.application.saga.reply.inventory.InventoryReserveFailedPayload;
import msa.bookloan.application.saga.reply.inventory.InventoryReservedPayload;
import msa.bookloan.application.saga.reply.member.MemberCheckedPayload;
import msa.bookloan.application.saga.reply.inventory.InventoryReserveFailedInternalEvent;
import msa.bookloan.application.saga.reply.inventory.InventoryReservedInternalEvent;
import msa.bookloan.application.saga.reply.member.MemberCheckedInternalEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReplyPayloadTranslator {

    private final ObjectMapper objectMapper;

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
