package msa.bookcatalog.adapter.out.messaging.outbox;

import msa.bookcatalog.infra.config.properties.OutboxSchedulerProps;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.adapter.out.persistence.outbox.EventRecorder;
import msa.bookcatalog.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayProcessorTest {

    @InjectMocks
    private OutboxRelayProcessor outboxRelayProcessor;

    @Mock
    private OutboxEventRecordRepository outboxEventRecordRepository;

    @Mock
    private EventRecorder eventRecorder;

    @Mock
    private OutboxSchedulerProps props;

    @Test
    @DisplayName("성공 시나리오: 예외(ex)가 null이면 이벤트를 'PUBLISHED'로 마킹한다")
    void updateStatusAfterProcessing_success() {
        // given
        Long eventId = 1L;
        String workerId = "worker-1";
        LocalDateTime claimedAt = LocalDateTime.now();

        // when
        outboxRelayProcessor.updateStatusAfterProcessing(eventId, workerId, claimedAt, null);

        // then
        // markPublishedByEventId가 정확한 인자와 함께 한 번 호출되었는지 검증
        verify(eventRecorder).markPublishedByEventId(eventId, workerId, claimedAt);
        // 다른 의존성은 전혀 호출되지 않았는지 검증
        verifyNoInteractions(outboxEventRecordRepository, props);
    }

    @Test
    @DisplayName("실패 시나리오: 재시도 횟수가 최대치 미만이면 'FAILED'로만 마킹한다")
    void updateStatusAfterProcessing_failure_underMaxRetry() {
        // given
        Long eventId = 2L;
        String workerId = "worker-1";
        LocalDateTime claimedAt = LocalDateTime.now();
        RuntimeException exception = new RuntimeException("Kafka Error");

        // 현재 재시도 횟수가 1인 레코드를 반환하도록 설정
        OutboxEventRecord record = createTestRecord(eventId, 1);
        when(outboxEventRecordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

        // 최대 재시도 횟수를 3으로 설정
        when(props.maxRetryCount()).thenReturn(3);
        // markFailedByEventId 호출 시 1(성공)을 반환하도록 설정
        when(eventRecorder.markFailedByEventId(anyLong(), anyString(), any(), anyString())).thenReturn(1);

        // when
        outboxRelayProcessor.updateStatusAfterProcessing(eventId, workerId, claimedAt, exception);

        // then
        // FAILED 마킹은 호출되었는지 검증
        verify(eventRecorder).markFailedByEventId(eventId, workerId, claimedAt, exception.toString());
        // DEAD LETTER 마킹은 호출되지 않았는지 검증
        verify(eventRecorder, never()).markDeadLetter(anyLong(), anyString());
    }

    @Test
    @DisplayName("실패 시나리오: 재시도 횟수가 최대치에 도달하면 'FAILED' 후 'DEAD_LETTER'로 마킹한다")
    void updateStatusAfterProcessing_failure_reachesMaxRetry() {
        // given
        Long eventId = 3L;
        String workerId = "worker-1";
        LocalDateTime claimedAt = LocalDateTime.now();
        RuntimeException exception = new RuntimeException("Kafka Error");

        // 현재 재시도 횟수가 2인 레코드를 반환 (다음 시도는 3번째가 됨)
        OutboxEventRecord record = createTestRecord(eventId, 2);
        when(outboxEventRecordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

        // 최대 재시도 횟수를 3으로 설정
        when(props.maxRetryCount()).thenReturn(3);
        when(eventRecorder.markFailedByEventId(anyLong(), anyString(), any(), anyString())).thenReturn(1);

        // when
        outboxRelayProcessor.updateStatusAfterProcessing(eventId, workerId, claimedAt, exception);

        // then
        // FAILED 마킹이 먼저 호출되었는지 검증
        verify(eventRecorder).markFailedByEventId(eventId, workerId, claimedAt, exception.toString());
        // 그 후 DEAD LETTER 마킹도 호출되었는지 검증
        verify(eventRecorder).markDeadLetter(eq(eventId), anyString());
    }

    @Test
    @DisplayName("실패 엣지 케이스: 재시도 횟수가 최대치에 도달해도 FAILED 업데이트가 실패하면 DEAD_LETTER로 마킹하지 않는다")
    void updateStatusAfterProcessing_failure_reachesMaxRetryButUpdateFails() {
        // given
        Long eventId = 4L;
        String workerId = "worker-1";
        LocalDateTime claimedAt = LocalDateTime.now();
        RuntimeException exception = new RuntimeException("Kafka Error");

        OutboxEventRecord record = createTestRecord(eventId, 2);
        when(outboxEventRecordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));
        //when(props.maxRetryCount()).thenReturn(3);
        // FAILED 마킹 시 0(실패)을 반환하도록 설정
        when(eventRecorder.markFailedByEventId(anyLong(), anyString(), any(), anyString())).thenReturn(0);

        // when
        outboxRelayProcessor.updateStatusAfterProcessing(eventId, workerId, claimedAt, exception);

        // then
        verify(eventRecorder).markFailedByEventId(eventId, workerId, claimedAt, exception.toString());
        // FAILED 업데이트가 실패했으므로(updated > 0 조건 false), DEAD LETTER는 호출되지 않아야 함
        verify(eventRecorder, never()).markDeadLetter(anyLong(), anyString());
    }

    private OutboxEventRecord createTestRecord(Long eventId, int retryCount) {
        return OutboxEventRecord.builder()
                .eventId(eventId)
                .retryCount(retryCount)
                .build();
    }
}