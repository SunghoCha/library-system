package msa.bookloan.adapter.out.messaging.outbox.scheduler;

import msa.bookloan.adapter.out.messaging.outbox.OutboxEventSender;
import msa.bookloan.adapter.out.persistence.outbox.OutboxClaimerService;
import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollingSchedulerTest {

    @InjectMocks
    private OutboxPollingScheduler outboxPollingScheduler;

    @Mock
    private OutboxEventSender outboxEventSender;

    @Mock
    private OutboxClaimerService outboxClaimerService;

    @Test
    @DisplayName("성공: 클레임된 이벤트가 있으면 모두 전송한다.")
    void pollAndPublish_Success() {
        // given
        OutboxEventRecord record1 = createMockRecord(1L);
        OutboxEventRecord record2 = createMockRecord(2L);
        List<OutboxEventRecord> targets = List.of(record1, record2);

        when(outboxClaimerService.claimEvents()).thenReturn(targets);

        doNothing().when(outboxEventSender).send(any(OutboxEventRecord.class));

        // when
        outboxPollingScheduler.pollAndPublish();

        // then
        verify(outboxClaimerService, times(1)).claimEvents();
        verify(outboxEventSender, times(1)).send(record1);
        verify(outboxEventSender, times(1)).send(record2);
        verify(outboxEventSender, times(targets.size())).send(any(OutboxEventRecord.class));
    }

    @Test
    @DisplayName("이벤트 없음: 클레임된 이벤트가 없으면 아무 작업도 하지 않는다.")
    void pollAndPublish() {
        // given
        when(outboxClaimerService.claimEvents()).thenReturn(List.of());

        // when
        outboxPollingScheduler.pollAndPublish();

        // then
        verify(outboxClaimerService, times(1)).claimEvents();
        verify(outboxEventSender, never()).send(any(OutboxEventRecord.class));
    }

    @Test
    @DisplayName("부분 실패: 이벤트 전송 중 예외가 발생해도 다음 이벤트를 계속 처리한다.")
    void pollAndPublish_PartialFailure() {
        // given
        OutboxEventRecord record1 = createMockRecord(1L);
        OutboxEventRecord record2 = createMockRecord(2L); // 이 이벤트에서 예외 발생
        OutboxEventRecord record3 = createMockRecord(3L);
        List<OutboxEventRecord> targets = List.of(record1, record2, record3);

        RuntimeException testException = new RuntimeException("Kafka Send Failed");

        when(outboxClaimerService.claimEvents()).thenReturn(targets);


        doNothing().when(outboxEventSender).send(record1);
        doNothing().when(outboxEventSender).send(record3);

        // record2 전송 시 예외 발생 설정
        doThrow(testException).when(outboxEventSender).send(record2);

        // then
        // 메서드 실행 시 예외가 밖으로 전파되지 않는지 확인 (try-catch)
        assertDoesNotThrow(() -> {
            outboxPollingScheduler.pollAndPublish();
        });

        // verify
        // claimEvents가 1번 호출되었는지 확인
        verify(outboxClaimerService, times(1)).claimEvents();

        // 예외 발생과 관계없이 3개 레코드 모두에 대해 send가 시도되었는지 확인
        verify(outboxEventSender, times(1)).send(record1);
        verify(outboxEventSender, times(1)).send(record2); // 예외가 발생한 호출
        verify(outboxEventSender, times(1)).send(record3); // 예외 후에도 호출됨

        // send가 총 3번 호출되었는지 확인
        verify(outboxEventSender, times(targets.size())).send(any(OutboxEventRecord.class));
    }

    private OutboxEventRecord createMockRecord(Long id) {
        return OutboxEventRecord.builder()
                .id(id)
                .eventId(1L)
                .build();
    }

}