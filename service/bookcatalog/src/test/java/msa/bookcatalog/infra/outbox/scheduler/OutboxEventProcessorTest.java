package msa.bookcatalog.infra.outbox.scheduler;

import msa.bookcatalog.infra.outbox.recorder.EventRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorTest {

    @InjectMocks
    private OutboxEventProcessor outboxEventProcessor;

    @Mock
    private EventRecorder eventRecorder;

    @Test
    @DisplayName("발행 성공 시 (예외가 null일 때), markAsPublished를 호출한다")
    void updateStatusAfterProcessing_whenSuccess_callsMarkAsPublished() {
        // given
        long eventId = 123L;

        // when
        outboxEventProcessor.updateStatusAfterProcessing(eventId, null);

        // then
        // 1. markAsPublished가 정확한 eventId로 1번 호출되었는지 검증
        verify(eventRecorder).markAsPublished(eventId);

        // 2. handleFailure는 절대 호출되지 않았는지 검증
        verify(eventRecorder, never()).handleFailure(anyLong(), anyString());
    }

    @Test
    @DisplayName("발행 실패 시 (예외가 있을 때), handleFailure를 호출한다")
    void updateStatusAfterProcessing_whenFailure_callsHandleFailure() {
        // given
        long eventId = 456L;
        RuntimeException exception = new RuntimeException("Test Kafka Exception");

        // when
        outboxEventProcessor.updateStatusAfterProcessing(eventId, exception);

        // then
        // 1. handleFailure가 정확한 eventId와 에러 메시지로 1번 호출되었는지 검증
        verify(eventRecorder).handleFailure(eventId, exception.getMessage());

        // 2. markAsPublished는 절대 호출되지 않았는지 검증
        verify(eventRecorder, never()).markAsPublished(anyLong());
    }

    @Test
    @DisplayName("상태 업데이트 중 예외 발생 시, 에러를 로깅하고 예외를 전파하지 않는다")
    void updateStatusAfterProcessing_whenInternalError_doesNotThrow() {
        // given
        long eventId = 789L;
        // Mock 설정: eventRecorder.markAsPublished가 호출되면 예외를 던지도록 설정
        doThrow(new RuntimeException("DB is down")).when(eventRecorder).markAsPublished(eventId);

        // when & then
        // 메서드 실행 시, 내부의 try-catch 블록이 예외를 처리하여 밖으로 전파하지 않는지 검증
        assertDoesNotThrow(() -> {
            outboxEventProcessor.updateStatusAfterProcessing(eventId, null);
        });

        // 예외가 발생했더라도, markAsPublished 호출 시도 자체는 있었는지 검증
        verify(eventRecorder).markAsPublished(eventId);
    }
}