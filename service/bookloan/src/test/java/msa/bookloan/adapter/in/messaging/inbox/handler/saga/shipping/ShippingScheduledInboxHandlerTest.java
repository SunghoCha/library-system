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
        Long eventId = 1001L;
        String sagaIdStr = "123";
        Long causationCmdIdStr = 2002L;
        Long loanVersion = 2L;
        Long shipmentId = 4004L;
        Long bookId = 3003L;
        String trackingNo = "track-abc";

        InboxMessage<ShippingScheduledReply> message = createTestMessage(
                eventId, sagaIdStr, causationCmdIdStr,
                loanVersion, shipmentId, bookId, trackingNo
        );

        // when
        handler.handle(message);

        // then
        ArgumentCaptor<ShippingScheduledInternalEvent> eventCaptor = ArgumentCaptor.captor();
        verify(orchestrator).onShippingScheduled(eventCaptor.capture());

        ShippingScheduledInternalEvent capturedEvent = eventCaptor.getValue();

        // 변환된 필드 검증
        assertThat(capturedEvent.sagaId()).isEqualTo(Long.parseLong(sagaIdStr));

        // Pss-through 필드 검증
        assertThat(capturedEvent.eventId()).isEqualTo(eventId);
        assertThat(capturedEvent.causationCommandId()).isEqualTo(causationCmdIdStr);
        assertThat(capturedEvent.loanVersion()).isEqualTo(loanVersion);
        assertThat(capturedEvent.shipmentId()).isEqualTo(shipmentId);
        assertThat(capturedEvent.bookId()).isEqualTo(bookId);
        assertThat(capturedEvent.trackingNo()).isEqualTo(trackingNo);
    }

    @Test
    @DisplayName("Payload의 필수 ID(sagaId)가 null일 경우 변환 중 예외가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenRequiredIdIsNull() {
        // given
        ShippingScheduledReply replyWithNull = new ShippingScheduledReply(
                1001L,
                null, // 문제 원인
                2002L,
                2L,
                4004L,
                3003L,
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
                1001L,
                "abc", // 문제 원인
                2002L,
                2L,
                4004L,
                3003L,
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
            Long eventId,
            String sagaIdStr,
            Long causationCommandId,
            Long loanVersion,
            Long shipmentId,
            Long bookId,
            String trackingNo
    ) {
        // 1. 페이로드 생성
        ShippingScheduledReply replyPayload = new ShippingScheduledReply(
                eventId,
                sagaIdStr,
                causationCommandId,
                loanVersion,
                shipmentId,
                bookId,
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