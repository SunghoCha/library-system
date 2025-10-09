package msa.bookcatalog.adapter.out.messaging.outbox;

import msa.bookcatalog.infra.config.properties.OutboxSchedulerProperties;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookcatalog.application.service.catalog.exception.OutboxEventRecordNotFoundException;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.snowflake.InstanceIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventSenderTest {

    @InjectMocks // @Mock으로 만든 가짜 객체들을 이 클래스에 주입
    private OutboxEventSender outboxEventSender;

    @Mock private InstanceIdentity identity;

    @Mock private OutboxSchedulerProperties props;

    @Mock private ImmediateClaimer claimer;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OutboxRelayProcessor outboxRelayProcessor;

    @Mock
    private OutboxEventRecordRepository outboxRepository;

    @Mock
    private CompletableFuture<SendResult<String, String>> mockFuture;

    // 비동기 콜백을 캡처하여 검증하기 위한 ArgumentCaptor (생소한 개념)
    @Captor
    private ArgumentCaptor<BiConsumer<SendResult<String, String>, Throwable>> callbackCaptor;

    private static final String TEST_WORKER_ID = "test-worker-01";

    @Test
    @DisplayName("send(): 성공 - 레코드 조회, 선점, 카프카 발행이 모두 성공한다")
    void send_success() {
        // given: 모든 의존성이 정상 동작하도록 설정
        long eventId = 1L;
        BookCatalogChangedEvent event = createTestEvent(eventId);
        OutboxEventRecord record = createTestRecord(eventId, true);

        when(outboxRepository.findByEventId(eventId)).thenReturn(Optional.of(record));
        when(identity.workerId()).thenReturn(TEST_WORKER_ID);
        when(props.leaseSeconds()).thenReturn(60);
        // 선점 성공 (true 반환)
        when(claimer.tryClaim(eq(eventId), eq(TEST_WORKER_ID), any(LocalDateTime.class), eq(60))).thenReturn(true);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(mockFuture);

        // when
        outboxEventSender.send(event);

        // then
        // 1. 선점 시도 검증
        verify(claimer).tryClaim(eq(eventId), eq(TEST_WORKER_ID), any(LocalDateTime.class), eq(60));
        // 2. Kafka 발행 검증
        verify(kafkaTemplate).send(record.getRouting().getTopic(), record.getRouting().getPartitionKey(), record.getPayload());
        // 3. 비동기 콜백 검증 (성공 시나리오)
        verify(mockFuture).whenComplete(callbackCaptor.capture());
        callbackCaptor.getValue().accept(null, null); // 성공 콜백 실행
        // 4. 최종적으로 Processor가 '성공' 상태로 호출되었는지 검증
        verify(outboxRelayProcessor).updateStatusAfterProcessing(eq(eventId), eq(TEST_WORKER_ID), any(LocalDateTime.class), isNull());
    }

    @Test
    @DisplayName("send(): 선점 실패 시, Kafka 발행 없이 조용히 종료된다")
    void send_claimFailed() {
        // given
        long eventId = 2L;
        BookCatalogChangedEvent event = createTestEvent(eventId);
        OutboxEventRecord record = createTestRecord(eventId, true);

        when(outboxRepository.findByEventId(eventId)).thenReturn(Optional.of(record));
        when(identity.workerId()).thenReturn(TEST_WORKER_ID);
        // 선점 실패 (false 반환)
        when(claimer.tryClaim(anyLong(), anyString(), any(LocalDateTime.class), anyInt())).thenReturn(false);

        // when
        outboxEventSender.send(event);

        // then
        // 선점 시도는 했지만, Kafka 발행은 절대 호출되지 않아야 함
        verify(claimer).tryClaim(anyLong(), anyString(), any(LocalDateTime.class), anyInt());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(outboxRelayProcessor, never()).updateStatusAfterProcessing(anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("send(): Outbox 레코드를 찾지 못하면 예외를 던진다")
    void send_recordNotFound() {
        // given
        when(outboxRepository.findByEventId(anyLong())).thenReturn(Optional.empty());

        // when & then
        assertThrows(OutboxEventRecordNotFoundException.class, () -> {
            outboxEventSender.send(createTestEvent(99L));
        });
    }

    @Test
    @DisplayName("send(): 레코드의 Routing 정보가 null이면 예외를 던진다")
    void send_routingIsNull() {
        // given
        OutboxEventRecord recordWithNullRouting = createTestRecord(3L, false);
        when(outboxRepository.findByEventId(3L)).thenReturn(Optional.of(recordWithNullRouting));

        // when & then
        assertThrows(IllegalStateException.class, () -> {
            outboxEventSender.send(createTestEvent(3L));
        });
    }

    private BookCatalogChangedEvent createTestEvent(Long eventId) {
        return BookCatalogChangedEvent.builder().eventId(eventId).bookId(eventId).build();
    }

    private OutboxEventRecord createTestRecord(Long eventId, boolean withRouting) {
        OutboxEventRecord.OutboxEventRecordBuilder builder = OutboxEventRecord.builder()
                .eventId(eventId)
                .payload("{\"message\":\"test payload\"}");

        if (withRouting) {
            builder.routing(OutboxRouting.builder()
                    .topic("test-topic")
                    .partitionKey(String.valueOf(eventId))
                    .build());
        }
        return builder.build();
    }
}