package msa.bookloan.application.event;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import msa.common.events.EventType;
import msa.common.events.inbox.InboxRecordableEvent;

/**
 * 사가 리플라이 공통 래퍼 (응답 채널 1개라서 구분용)
 * eventId: 리플라이 메시지 자체의 id (snowflake로 생성)
 * replyType: MemberChecked , InventoryReserved,  InventoryReserveFailed ...
 */
public record SagaReplyEnvelope(
        @NotNull @Pattern(regexp = "^[0-9]+$") String eventId,
        @NotBlank @Pattern(regexp = "^[0-9]+$") String sagaId,
        @NotBlank String replyType,
        @Pattern(regexp = "^[0-9]+$") String causationCommandId,
        @NotBlank @Pattern(regexp = "^[0-9]+$") String aggregateId,
        Long sourceAggregateVersion,
        @NotNull JsonNode payload
) implements InboxRecordableEvent {
    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public String getAggregateId() {
        return aggregateId;
    }

    @Override
    public Long getAggregateVersion() {
        return sourceAggregateVersion; // bookLoan version
    }

    @Override
    public String getEventType() {
        return EventType.SAGA_REPLY.name();
    }
}
