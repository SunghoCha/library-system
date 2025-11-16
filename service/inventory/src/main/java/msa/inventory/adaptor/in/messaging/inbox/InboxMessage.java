package msa.inventory.adaptor.in.messaging.inbox;

import java.util.Objects;
/**
 * Inbox 처리를 위해 핸들러에게 전달되는 통합 메시지 객체
 *
 * @param <T>         페이로드의 타입
 * @param payload     역직렬화된 실제 이벤트 페이로드
 * @param eventId     인박스 이벤트 레코드의 고유 ID
 * @param aggregateId 이벤트가 발생한 Aggregate의 ID
 * @param aggregateVersion 이벤트가 발생한 Aggregate의 버전 (프로젝션 멱등성 처리용) 사가에선 null 가능
 * @param eventType   이벤트 타입
 */
public record InboxMessage<T>(
        T payload,
        Long eventId,
        Long aggregateId,
        Long aggregateVersion,
        String eventType
) {
    public InboxMessage {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        // aggregateId는 null일 수 있음 (선택적)
    }
}