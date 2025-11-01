package msa.bookloan.adapter.in.messaging.inbox.handler.saga.inventory;

import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.inventory.InventoryReserveFailedInternalEvent;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReserveFailedReply;
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
class InventoryReserveFailedInboxHandlerTest {

    @InjectMocks
    private InventoryReserveFailedInboxHandler handler;

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
        String reasonCode = "OUT_OF_STOCK";
        String messageStr = "Inventory not available";

        InboxMessage<InventoryReserveFailedReply> message = createTestMessage(
                eventIdStr, sagaIdStr, causationCmdIdStr,
                loanVersion, bookIdStr, reasonCode, messageStr
        );

        // when
        handler.handle(message);

        // then
        ArgumentCaptor<InventoryReserveFailedInternalEvent> eventCaptor = ArgumentCaptor.captor();
        verify(orchestrator).onInventoryReserveFailed(eventCaptor.capture());

        InventoryReserveFailedInternalEvent capturedEvent = eventCaptor.getValue();

        assertThat(capturedEvent.eventId()).isEqualTo(Long.parseLong(eventIdStr));
        assertThat(capturedEvent.sagaId()).isEqualTo(Long.parseLong(sagaIdStr));
        assertThat(capturedEvent.causationCommandId()).isEqualTo(Long.parseLong(causationCmdIdStr));
        assertThat(capturedEvent.loanVersion()).isEqualTo(loanVersion);
        assertThat(capturedEvent.bookId()).isEqualTo(Long.parseLong(bookIdStr));
        assertThat(capturedEvent.reasonCode()).isEqualTo(reasonCode);
        assertThat(capturedEvent.message()).isEqualTo(messageStr);
    }

    @Test
    @DisplayName("Payload의 필수 ID가 null일 경우 변환 중 예외가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenRequiredIdIsNull() {
        // given
        InventoryReserveFailedReply replyWithNull = new InventoryReserveFailedReply(
                "1001",
                null, // 문제 원인
                "2002",
                2L,
                "3003",
                "REASON",
                "msg"
        );

        InboxMessage<InventoryReserveFailedReply> message = new InboxMessage<>(
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
        verify(orchestrator, never()).onInventoryReserveFailed(any());
    }

    @Test
    @DisplayName("Payload의 필수 ID가 숫자가 아닐 경우 변환 중 예외(IllegalArgumentException)가 발생해야 한다")
    void handle_ShouldThrowExceptionWhenIdIsNotNumeric() {
        // given
        InventoryReserveFailedReply replyWithBadFormat = new InventoryReserveFailedReply(
                "1001",
                "abc", // 문제 원인
                "2002",
                2L,
                "3003",
                "REASON",
                "msg"
        );

        InboxMessage<InventoryReserveFailedReply> message = new InboxMessage<>(
                replyWithBadFormat,
                999L,
                null,
                null,
                handler.eventType()
        );

        // when & then
        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(IllegalArgumentException.class);

        verify(orchestrator, never()).onInventoryReserveFailed(any());
    }

    private InboxMessage<InventoryReserveFailedReply> createTestMessage(
            String eventIdStr,
            String sagaIdStr,
            String causationCmdIdStr,
            Long loanVersion,
            String bookIdStr,
            String reasonCode,
            String message
    ) {
        // 1. 페이로드 생성
        InventoryReserveFailedReply replyPayload = new InventoryReserveFailedReply(
                eventIdStr,
                sagaIdStr,
                causationCmdIdStr,
                loanVersion,
                bookIdStr,
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