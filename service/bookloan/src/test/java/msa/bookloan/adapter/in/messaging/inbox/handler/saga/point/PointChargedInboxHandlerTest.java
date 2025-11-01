package msa.bookloan.adapter.in.messaging.inbox.handler.saga.point;

import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.point.PointChargedInternalEvent;
import msa.common.events.bookloan.saga.reply.point.PointChargedReply;
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
class PointChargedInboxHandlerTest {

    @InjectMocks
    private PointChargedInboxHandler handler;

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
        Long amount = 100L;
        String paymentIdStr = "4004";

        InboxMessage<PointChargedReply> message = createTestMessage(
                eventIdStr, sagaIdStr, causationCmdIdStr,
                loanVersion, memberIdStr, amount, paymentIdStr
        );

        // when
        handler.handle(message);

        // then
        ArgumentCaptor<PointChargedInternalEvent> eventCaptor = ArgumentCaptor.captor();
        verify(orchestrator).onPointCharged(eventCaptor.capture());

        PointChargedInternalEvent capturedEvent = eventCaptor.getValue();

        assertThat(capturedEvent.eventId()).isEqualTo(Long.parseLong(eventIdStr));
        assertThat(capturedEvent.sagaId()).isEqualTo(Long.parseLong(sagaIdStr));
        assertThat(capturedEvent.causationCommandId()).isEqualTo(Long.parseLong(causationCmdIdStr));
        assertThat(capturedEvent.loanVersion()).isEqualTo(loanVersion);
        assertThat(capturedEvent.memberId()).isEqualTo(Long.parseLong(memberIdStr));
        assertThat(capturedEvent.amount()).isEqualTo(amount);
        assertThat(capturedEvent.paymentId()).isEqualTo(Long.parseLong(paymentIdStr));
    }

    @Test
    @DisplayName("Payload의 필수 ID가 null일 경우 변환 중 예외가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenRequiredIdIsNull() {
        // given
        PointChargedReply replyWithNull = new PointChargedReply(
                "1001",
                null, // 문제 원인
                "2002",
                2L,
                "3003",
                100L,
                "4004"
        );

        InboxMessage<PointChargedReply> message = new InboxMessage<>(
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
        verify(orchestrator, never()).onPointCharged(any());
    }

    @Test
    @DisplayName("Payload의 필수 ID가 숫자가 아닐 경우 변환 중 예외(IllegalArgumentException)가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenIdIsNotNumeric() {
        // given
        PointChargedReply replyWithBadFormat = new PointChargedReply(
                "1001",
                "abc", // 문제 원인
                "2002",
                2L,
                "3003",
                100L,
                "4004"
        );

        InboxMessage<PointChargedReply> message = new InboxMessage<>(
                replyWithBadFormat,
                999L,
                null,
                null,
                handler.eventType()
        );

        // when & then
        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(IllegalArgumentException.class);

        verify(orchestrator, never()).onPointCharged(any());
    }

    private InboxMessage<PointChargedReply> createTestMessage(
            String eventIdStr,
            String sagaIdStr,
            String causationCmdIdStr,
            Long loanVersion,
            String memberIdStr,
            Long amount,
            String paymentIdStr
    ) {
        // 1. 페이로드 생성
        PointChargedReply replyPayload = new PointChargedReply(
                eventIdStr,
                sagaIdStr,
                causationCmdIdStr,
                loanVersion,
                memberIdStr,
                amount,
                paymentIdStr
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