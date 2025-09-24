package msa.bookcatalog.infra.outbox;

import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecord;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecordRepository;
import msa.bookcatalog.infra.outbox.scheduler.OutboxEventProcessor;
import msa.bookcatalog.service.exception.OutboxEventRecordNotFoundException;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.events.outbox.dto.OutboxRouting;
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

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OutboxEventProcessor outboxEventProcessor;

    @Mock
    private BookCatalogOutboxEventRecordRepository outboxRepository;

    @Mock
    private CompletableFuture<SendResult<String, String>> mockFuture;

    // 비동기 콜백을 캡처하여 검증하기 위한 ArgumentCaptor (생소한 개념)
    @Captor
    private ArgumentCaptor<BiConsumer<SendResult<String, String>, Throwable>> callbackCaptor;

    @Test
    @DisplayName("send() 이벤트 발행 성공 시, Kafka로 메시지를 보내고 Processor를 호출한다")
    void send_success() {
        // given
        long eventId = 123L;
        BookCatalogChangedEvent event = createTestEvent(eventId);
        BookCatalogOutboxEventRecord record = createTestRecord(eventId);

        // Repository가 테스트용 레코드를 반환하도록 설정
        when(outboxRepository.findByEventId(eventId)).thenReturn(Optional.of(record));
        // KafkaTemplate.send()가 성공한 비동기 결과를 반환하도록 설정
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(mockFuture);

        // when
        outboxEventSender.send(event);

        // then
        // 1. Repository의 findByEventId가 정확한 eventId로 호출되었는지 검증
        verify(outboxRepository).findByEventId(eventId);
        // 2. KafkaTemplate.send가 레코드의 정보로 올바르게 호출되었는지 검증
        verify(kafkaTemplate).send(record.getRouting().getTopic(), record.getRouting().getPartitionKey(), record.getPayload());

        // 3. whenComplete에 전달된 콜백을 캡처하여, 성공 시나리오를 시뮬레이션
        verify(mockFuture).whenComplete(callbackCaptor.capture());
        callbackCaptor.getValue().accept(null, null); // 성공 콜백 실행 (result=null, ex=null)

        // 4. 성공 콜백이 실행된 후, Processor가 올바르게 호출되었는지 검증
        verify(outboxEventProcessor).updateStatusAfterProcessing(eventId, null);
    }

    @Test
    @DisplayName("send(): Outbox 레코드를 찾지 못하면 예외를 던진다")
    void send_recordNotFound() {
        // given
        long eventId = 789L;
        BookCatalogChangedEvent event = createTestEvent(eventId);
        when(outboxRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(OutboxEventRecordNotFoundException.class, () -> {
            outboxEventSender.send(event);
        });

        // KafkaTemplate.send가 절대 호출되지 않았는지 검증
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("send(): Kafka 발행 실패 시, 예외와 함께 Processor를 호출한다")
    void send_kafkaFailure() {
        // given
        long eventId = 456L;
        BookCatalogChangedEvent event = createTestEvent(eventId);
        BookCatalogOutboxEventRecord record = createTestRecord(eventId);
        // 테스트용 가짜 예외 생성
        RuntimeException kafkaException = new RuntimeException("Kafka connection failed");

        when(outboxRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

        // Mock 설정: KafkaTemplate.send()가 '실패한' 비동기 결과를 반환하도록 설정
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(mockFuture);

        // when
        outboxEventSender.send(event);

        // then
        // 1. whenComplete 콜백을 캡처하여, 실패 시나리오를 시뮬레이션
        verify(mockFuture).whenComplete(callbackCaptor.capture());

        // 2. 캡처한 콜백을 '실패' 상황으로 직접 실행
        // 첫 번째 인자(result)는 null, 두 번째 인자(ex)에 예외 객체를 전달
        callbackCaptor.getValue().accept(null, kafkaException);

        // 3. 실패 콜백이 실행된 후, Processor가 '예외 객체'와 함께 올바르게 호출되었는지 검증
        verify(outboxEventProcessor).updateStatusAfterProcessing(eventId, kafkaException);
    }

    @Test
    @DisplayName("resend(): 이벤트 재발행 성공 시, Kafka로 메시지를 보내고 Processor를 호출한다")
    void resend_success() {
        // given
        long eventId = 111L;
        BookCatalogOutboxEventRecord record = createTestRecord(eventId);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(mockFuture);

        // when
        outboxEventSender.resend(record);

        // then
        verify(kafkaTemplate).send(record.getRouting().getTopic(), record.getRouting().getPartitionKey(), record.getPayload());

        verify(mockFuture).whenComplete(callbackCaptor.capture());
        callbackCaptor.getValue().accept(null, null);

        verify(outboxEventProcessor).updateStatusAfterProcessing(eventId, null);
    }

    @Test
    @DisplayName("resend(): Kafka 재발행 실패 시, 예외와 함께 Processor를 호출한다")
    void resend_kafkaFailure() {
        // given
        long eventId = 222L;
        BookCatalogOutboxEventRecord record = createTestRecord(eventId);
        RuntimeException kafkaException = new RuntimeException("Kafka connection failed");

        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(mockFuture);

        // when
        outboxEventSender.resend(record);

        // then
        verify(mockFuture).whenComplete(callbackCaptor.capture());
        callbackCaptor.getValue().accept(null, kafkaException);

        verify(outboxEventProcessor).updateStatusAfterProcessing(eventId, kafkaException);
    }

    @Test
    @DisplayName("resend(): 레코드의 Routing 정보가 null이면 IllegalStateException을 던진다")
    void resend_routingIsNull() {
        // given
        long eventId = 333L;
        // routing 정보가 없는 레코드 생성
        BookCatalogOutboxEventRecord recordWithNullRouting = BookCatalogOutboxEventRecord.builder()
                .eventId(eventId)
                .payload("{\"message\":\"test\"}")
                .routing(null) // routing 필드를 null로 설정
                .build();

        // when & then
        assertThrows(IllegalStateException.class, () -> {
            outboxEventSender.resend(recordWithNullRouting);
        });
    }

    private BookCatalogChangedEvent createTestEvent(Long eventId) {
        return BookCatalogChangedEvent.builder().eventId(eventId).bookId(eventId).build();
    }

    private BookCatalogOutboxEventRecord createTestRecord(Long eventId) {
        return BookCatalogOutboxEventRecord.builder()
                .eventId(eventId)
                .payload("{\"message\":\"test payload\"}")
                .routing(OutboxRouting.builder()
                        .topic("test-topic")
                        .partitionKey(String.valueOf(eventId))
                        .build())
                .build();
    }
}