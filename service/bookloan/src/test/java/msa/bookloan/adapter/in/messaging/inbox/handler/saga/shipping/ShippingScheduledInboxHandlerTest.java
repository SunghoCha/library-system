package msa.bookloan.adapter.in.messaging.inbox.handler.saga.shipping;

import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledInternalEvent;
import msa.common.events.bookloan.saga.reply.shipping.ShippingScheduledReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShippingScheduledInboxHandlerTest {

    @InjectMocks
    private ShippingScheduledInboxHandler handler;

    @Mock
    private LoanRequestSagaOrchestrator orchestrator;

    @Test
    @DisplayName("핸들러는 수신한 InboxMessage를 InternalEvent로 변환하여 오케스트레이터에 전달한다")
    void handle_ShouldDelegateToOrchestrator() {
        // given
        String eventIdStr = "1001";
        String sagaIdStr = "123";
        String causationCmdIdStr = "2002";
        Long loanVersion = 2L;
        String shipmentIdStr = "4004";
        String bookIdStr = "3003";
        String trackingNo = "track-abc";

        // String 기반의 헬퍼 메서드 호출
        InboxMessage<ShippingScheduledReply> message = createTestMessage(
                eventIdStr, sagaIdStr, causationCmdIdStr,
                loanVersion, shipmentIdStr, bookIdStr, trackingNo
        );
        // when
        handler.handle(message);

        // then
        ArgumentCaptor<ShippingScheduledInternalEvent> eventCaptor = ArgumentCaptor.captor();
        verify(orchestrator).onShippingScheduled(eventCaptor.capture());

        ShippingScheduledInternalEvent capturedEvent = eventCaptor.getValue();

        // 변환된 필드 검증
        assertThat(capturedEvent.sagaId()).isEqualTo(Long.parseLong(sagaIdStr));

        assertThat(capturedEvent.eventId()).isEqualTo(Long.parseLong(eventIdStr));
        assertThat(capturedEvent.sagaId()).isEqualTo(Long.parseLong(sagaIdStr));
        assertThat(capturedEvent.causationCommandId()).isEqualTo(Long.parseLong(causationCmdIdStr));
        assertThat(capturedEvent.shipmentId()).isEqualTo(Long.parseLong(shipmentIdStr));
        assertThat(capturedEvent.bookId()).isEqualTo(Long.parseLong(bookIdStr));

        assertThat(capturedEvent.loanVersion()).isEqualTo(loanVersion);
        assertThat(capturedEvent.trackingNo()).isEqualTo(trackingNo);
    }

    @Test
    @DisplayName("Payload의 필수 ID(sagaId)가 null일 경우 변환 중 예외가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenRequiredIdIsNull() {
        // given
        ShippingScheduledReply replyWithNull = new ShippingScheduledReply(
                "1001",
                null,
                "2002",
                2L,
                "4004",
                "3003",
                "track-abc"
        );

        InboxMessage<ShippingScheduledReply> message = new InboxMessage<>(
                replyWithNull,
                999L,
                null,
                null,
                handler.eventType()
        );

        // when & then
        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(IllegalArgumentException.class);

        // 예외가 발생했으므로 orchestrator는 절대 호출되면 안 됨
        verify(orchestrator, never()).onShippingScheduled(any());
    }

    @Test
    @DisplayName("Payload의 필수 ID(sagaId)가 숫자가 아닐 경우 변환 중 예외(IllegalArgumentException)가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenIdIsNotNumeric() {
        // given
        ShippingScheduledReply replyWithBadFormat = new ShippingScheduledReply(
                "1001",
                "abc",
                "2002",
                2L,
                "4004",
                "3003",
                "track-abc"
        );

        InboxMessage<ShippingScheduledReply> message = new InboxMessage<>(
                replyWithBadFormat,
                999L,
                null,
                null,
                handler.eventType()
        );

        // when & then
        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(IllegalArgumentException.class);

        verify(orchestrator, never()).onShippingScheduled(any());
    }

    private InboxMessage<ShippingScheduledReply> createTestMessage(
            String eventIdStr,
            String sagaIdStr,
            String causationCommandIdStr,
            Long loanVersion,
            String shipmentIdStr,
            String bookIdStr,
            String trackingNo
    ) {
        // 1. 페이로드 생성
        ShippingScheduledReply replyPayload = new ShippingScheduledReply(
                eventIdStr,
                sagaIdStr,
                causationCommandIdStr,
                loanVersion,
                shipmentIdStr,
                bookIdStr,
                trackingNo
        );

        Long inboxEventId = 999L;
        Long inboxAggregateId = Long.parseLong(sagaIdStr); // 페이로드의 sagaId

        return new InboxMessage<>(
                replyPayload,
                inboxEventId,
                inboxAggregateId,
                null, // 사가에선 버전정보 불필요
                handler.eventType()
        );
    }
}