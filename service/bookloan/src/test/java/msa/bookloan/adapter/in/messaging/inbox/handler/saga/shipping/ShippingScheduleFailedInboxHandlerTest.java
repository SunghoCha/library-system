package msa.bookloan.adapter.in.messaging.inbox.handler.saga.shipping;

import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedInternalEvent;
import msa.common.events.bookloan.saga.reply.shipping.ShippingScheduleFailedReply;
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
class ShippingScheduleFailedInboxHandlerTest {

    @InjectMocks
    private ShippingScheduleFailedInboxHandler handler;

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
        String reasonCode = "DELIVERY_ERROR";
        String messageStr = "Failed to schedule";

        // String 기반의 헬퍼 메서드 호출
        InboxMessage<ShippingScheduleFailedReply> message = createTestMessage(
                eventIdStr, sagaIdStr, causationCmdIdStr,
                loanVersion, reasonCode, messageStr
        );

        // when
        handler.handle(message);

        // then
        ArgumentCaptor<ShippingScheduleFailedInternalEvent> eventCaptor = ArgumentCaptor.captor();
        verify(orchestrator).onShippingScheduleFailed(eventCaptor.capture());

        ShippingScheduleFailedInternalEvent capturedEvent = eventCaptor.getValue();

        // 변환된 필드 검증
        assertThat(capturedEvent.sagaId()).isEqualTo(Long.parseLong(sagaIdStr));

        assertThat(capturedEvent.eventId()).isEqualTo(Long.parseLong(eventIdStr));
        assertThat(capturedEvent.sagaId()).isEqualTo(Long.parseLong(sagaIdStr));
        assertThat(capturedEvent.causationCommandId()).isEqualTo(Long.parseLong(causationCmdIdStr));

        assertThat(capturedEvent.loanVersion()).isEqualTo(loanVersion);
        assertThat(capturedEvent.reasonCode()).isEqualTo(reasonCode);
        assertThat(capturedEvent.message()).isEqualTo(messageStr);
    }

    @Test
    @DisplayName("Payload의 필수 ID(sagaId)가 null일 경우 변환 중 예외가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenRequiredIdIsNull() {
        // given
        ShippingScheduleFailedReply replyWithNull = new ShippingScheduleFailedReply(
                "1001", // eventId (유효한 숫자 문자열)
                null,   // sagaId (테스트 대상)
                "2002", // causationCommandId (유효한 숫자 문자열)
                2L,     // loanVersion
                "REASON",
                "msg"
        );

        InboxMessage<ShippingScheduleFailedReply> message = new InboxMessage<>(
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
        verify(orchestrator, never()).onShippingScheduleFailed(any());
    }

    @Test
    @DisplayName("Payload의 필수 ID(sagaId)가 숫자가 아닐 경우 변환 중 예외(IllegalArgumentException)가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenIdIsNotNumeric() {
        // given
        ShippingScheduleFailedReply replyWithBadFormat = new ShippingScheduleFailedReply(
                "1001", // eventId (유효한 숫자 문자열)
                "abc",  // sagaId (테스트 대상 - 숫자가 아님)
                "2002", // causationCommandId (유효한 숫자 문자열)
                2L,     // loanVersion
                "REASON",
                "msg"
        );

        InboxMessage<ShippingScheduleFailedReply> message = new InboxMessage<>(
                replyWithBadFormat,
                999L,
                null,
                null,
                handler.eventType()
        );

        // when & then
        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(IllegalArgumentException.class);

        verify(orchestrator, never()).onShippingScheduleFailed(any());
    }

    private InboxMessage<ShippingScheduleFailedReply> createTestMessage(
            String eventIdStr,
            String sagaIdStr,
            String causationCommandIdStr,
            Long loanVersion,
            String reasonCode,
            String message
    ) {
        // 1. 페이로드 생성
        ShippingScheduleFailedReply replyPayload = new ShippingScheduleFailedReply(
                eventIdStr,
                sagaIdStr,
                causationCommandIdStr,
                loanVersion,
                reasonCode,
                message
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