package msa.bookloan.adapter.in.messaging.inbox.handler.saga.shipping;

import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.shipping.ShippingAcceptedInternalEvent;
import msa.common.events.bookloan.saga.reply.shipping.ShippingAcceptedReply;
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
class ShippingAcceptedInboxHandlerTest {

    @InjectMocks
    private ShippingAcceptedInboxHandler handler;

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
        String bookIdStr = "3003";
        String provisionalShipmentIdStr = "4004";
        String trackingNoPreview = "track-abc";

        InboxMessage<ShippingAcceptedReply> message = createTestMessage(
                eventIdStr, sagaIdStr, causationCmdIdStr,
                loanVersion, bookIdStr, provisionalShipmentIdStr, trackingNoPreview
        );

        // when
        handler.handle(message);

        // then
        ArgumentCaptor<ShippingAcceptedInternalEvent> eventCaptor = ArgumentCaptor.captor();
        verify(orchestrator).onShippingAccepted(eventCaptor.capture());

        ShippingAcceptedInternalEvent capturedEvent = eventCaptor.getValue();

        // 변환된 필드 검증
        assertThat(capturedEvent.sagaId()).isEqualTo(Long.parseLong(sagaIdStr));

        assertThat(capturedEvent.eventId()).isEqualTo(Long.parseLong(eventIdStr));
        assertThat(capturedEvent.sagaId()).isEqualTo(Long.parseLong(sagaIdStr));
        assertThat(capturedEvent.causationCommandId()).isEqualTo(Long.parseLong(causationCmdIdStr));
        assertThat(capturedEvent.bookId()).isEqualTo(Long.parseLong(bookIdStr));
        assertThat(capturedEvent.provisionalShipmentId()).isEqualTo(Long.parseLong(provisionalShipmentIdStr));

        assertThat(capturedEvent.loanVersion()).isEqualTo(loanVersion);
        assertThat(capturedEvent.trackingNoPreview()).isEqualTo(trackingNoPreview);
    }

    @Test
    @DisplayName("Payload의 필수 ID(sagaId)가 null일 경우 변환 중 예외가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenRequiredIdIsNull() {
        // given
        ShippingAcceptedReply replyWithNull = new ShippingAcceptedReply(
                "1001",
                null,
                "2002",
                2L,
                "100",
                "3003",
                null
        );

        InboxMessage<ShippingAcceptedReply> message = new InboxMessage<>(
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
        verify(orchestrator, never()).onShippingAccepted(any());
    }

    @Test
    @DisplayName("Payload의 필수 ID(sagaId)가 숫자가 아닐 경우 변환 중 예외(IllegalArgumentException)가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenIdIsNotNumeric() {
        // given
        ShippingAcceptedReply replyWithBadFormat = new ShippingAcceptedReply(
                "1001",
                "abc",
                "2002",
                2L,
                "3003",
                null,
                null
        );

        InboxMessage<ShippingAcceptedReply> message = new InboxMessage<>(
                replyWithBadFormat,
                999L,
                null,
                null,
                handler.eventType()
        );

        // when & then
        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(IllegalArgumentException.class);

        verify(orchestrator, never()).onShippingAccepted(any());
    }

    private InboxMessage<ShippingAcceptedReply> createTestMessage(
            String eventIdStr,
            String sagaIdStr,
            String causationCommandIdStr,
            Long loanVersion,
            String bookIdStr,
            String provisionalShipmentIdStr,
            String trackingNoPreview
    ) {
        // 1. 페이로드 생성
        ShippingAcceptedReply replyPayload = new ShippingAcceptedReply(
                eventIdStr,
                sagaIdStr,
                causationCommandIdStr,
                loanVersion,
                bookIdStr,
                provisionalShipmentIdStr,
                trackingNoPreview
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