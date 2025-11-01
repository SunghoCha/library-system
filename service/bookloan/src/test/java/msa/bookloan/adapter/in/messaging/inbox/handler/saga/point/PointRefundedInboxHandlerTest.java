package msa.bookloan.adapter.in.messaging.inbox.handler.saga.point;

import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.point.PointRefundedInternalEvent;
import msa.common.events.bookloan.saga.reply.point.PointRefundedReply;
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
class PointRefundedInboxHandlerTest {

    @InjectMocks
    private PointRefundedInboxHandler handler;

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
        String memberIdStr = "3003";
        Long refundAmount = 500L;
        String refundId = "refund-tx-id-4004";

        InboxMessage<PointRefundedReply> message = createTestMessage(
                eventIdStr, sagaIdStr, causationCmdIdStr,
                loanVersion, memberIdStr, refundAmount, refundId
        );

        // when
        handler.handle(message);

        // then
        ArgumentCaptor<PointRefundedInternalEvent> eventCaptor = ArgumentCaptor.captor();
        verify(orchestrator).onPointRefunded(eventCaptor.capture());

        PointRefundedInternalEvent capturedEvent = eventCaptor.getValue();

        assertThat(capturedEvent.eventId()).isEqualTo(Long.parseLong(eventIdStr));
        assertThat(capturedEvent.sagaId()).isEqualTo(Long.parseLong(sagaIdStr));
        assertThat(capturedEvent.causationCommandId()).isEqualTo(Long.parseLong(causationCmdIdStr));
        assertThat(capturedEvent.loanVersion()).isEqualTo(loanVersion);
        assertThat(capturedEvent.memberId()).isEqualTo(Long.parseLong(memberIdStr));
        assertThat(capturedEvent.refundAmount()).isEqualTo(refundAmount);
        assertThat(capturedEvent.refundId()).isEqualTo(refundId);
    }

    @Test
    @DisplayName("Payload의 필수 ID가 null일 경우 변환 중 예외가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenRequiredIdIsNull() {
        // given
        PointRefundedReply replyWithNull = new PointRefundedReply(
                "1001",
                null, // 문제 원인
                "2002",
                2L,
                "3003",
                500L,
                "refund-tx-id"
        );

        InboxMessage<PointRefundedReply> message = new InboxMessage<>(
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
        verify(orchestrator, never()).onPointRefunded(any());
    }

    @Test
    @DisplayName("Payload의 필수 ID가 숫자가 아닐 경우 변환 중 예외(IllegalArgumentException)가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenIdIsNotNumeric() {
        // given
        PointRefundedReply replyWithBadFormat = new PointRefundedReply(
                "1001",
                "abc", // 문제 원인
                "2002",
                2L,
                "3003",
                500L,
                "refund-tx-id"
        );

        InboxMessage<PointRefundedReply> message = new InboxMessage<>(
                replyWithBadFormat,
                999L,
                null,
                null,
                handler.eventType()
        );

        // when & then
        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(IllegalArgumentException.class);

        verify(orchestrator, never()).onPointRefunded(any());
    }

    private InboxMessage<PointRefundedReply> createTestMessage(
            String eventIdStr,
            String sagaIdStr,
            String causationCmdIdStr,
            Long loanVersion,
            String memberIdStr,
            Long refundAmount,
            String refundId
    ) {
        // 1. 페이로드 생성
        PointRefundedReply replyPayload = new PointRefundedReply(
                eventIdStr,
                sagaIdStr,
                causationCmdIdStr,
                loanVersion,
                memberIdStr,
                refundAmount,
                refundId
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