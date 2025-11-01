package msa.bookloan.adapter.in.messaging.inbox.handler.saga.point;

import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.point.PointChargeFailedInternalEvent;
import msa.common.events.bookloan.saga.reply.point.PointChargeFailedReply;
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
class PointChargeFailedInboxHandlerTest {

    @InjectMocks
    private PointChargeFailedInboxHandler handler;

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
        String reasonCode = "INSUFFICIENT_FUNDS";
        String messageStr = "Not enough points";

        InboxMessage<PointChargeFailedReply> message = createTestMessage(
                eventIdStr, sagaIdStr, causationCmdIdStr,
                loanVersion, reasonCode, messageStr
        );

        // when
        handler.handle(message);

        // then
        ArgumentCaptor<PointChargeFailedInternalEvent> eventCaptor = ArgumentCaptor.captor();
        verify(orchestrator).onPointChargeFailed(eventCaptor.capture());

        PointChargeFailedInternalEvent capturedEvent = eventCaptor.getValue();

        assertThat(capturedEvent.eventId()).isEqualTo(Long.parseLong(eventIdStr));
        assertThat(capturedEvent.sagaId()).isEqualTo(Long.parseLong(sagaIdStr));
        assertThat(capturedEvent.causationCommandId()).isEqualTo(Long.parseLong(causationCmdIdStr));
        assertThat(capturedEvent.loanVersion()).isEqualTo(loanVersion);
        assertThat(capturedEvent.reasonCode()).isEqualTo(reasonCode);
        assertThat(capturedEvent.message()).isEqualTo(messageStr);
    }

    @Test
    @DisplayName("Payload의 필수 ID가 null일 경우 변환 중 예외가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenRequiredIdIsNull() {
        // given
        PointChargeFailedReply replyWithNull = new PointChargeFailedReply(
                "1001",
                null, // 문제 원인
                "2002",
                2L,
                "REASON",
                "msg"
        );

        InboxMessage<PointChargeFailedReply> message = new InboxMessage<>(
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
        verify(orchestrator, never()).onPointChargeFailed(any());
    }

    @Test
    @DisplayName("Payload의 필수 ID가 숫자가 아닐 경우 변환 중 예외(IllegalArgumentException)가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenIdIsNotNumeric() {
        // given
        PointChargeFailedReply replyWithBadFormat = new PointChargeFailedReply(
                "1001",
                "abc", // 문제 원인
                "2002",
                2L,
                "REASON",
                "msg"
        );

        InboxMessage<PointChargeFailedReply> message = new InboxMessage<>(
                replyWithBadFormat,
                999L,
                null,
                null,
                handler.eventType()
        );

        // when & then
        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(IllegalArgumentException.class);

        verify(orchestrator, never()).onPointChargeFailed(any());
    }

    private InboxMessage<PointChargeFailedReply> createTestMessage(
            String eventIdStr,
            String sagaIdStr,
            String causationCmdIdStr,
            Long loanVersion,
            String reasonCode,
            String message
    ) {
        // 1. 페이로드 생성
        PointChargeFailedReply replyPayload = new PointChargeFailedReply(
                eventIdStr,
                sagaIdStr,
                causationCmdIdStr,
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