package msa.bookloan.adapter.out.messaging.inbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import msa.bookloan.adapter.out.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.adapter.out.persistence.inbox.entity.InboxEventRecord;
import msa.bookloan.adapter.out.persistence.inbox.repository.InboxEventRecordRepository;
import msa.bookloan.testsupport.time.TestClocks;
import msa.common.config.properties.InboxProcessingProps;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.exception.BusinessNotRetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboxEventDispatcherTest {

    private InboxEventDispatcher dispatcher;

    @Mock
    private InboxEventRecordRepository recordRepository;

    @Mock
    private InboxStatusMarker inboxStatusMarker;

    @Mock
    private InboxProcessingProps inboxProcessingProps;

    @Mock
    private InboxEventHandler<TestPayload> testEventHandler;

    private final Clock fixedClock = TestClocks.FIXED_CLOCK;

    private final ObjectMapper objectMapper = new Jackson2ObjectMapperBuilder().build();

    record TestPayload(String message) {}

    private final Long eventId = 1L;
    private final String leaseId = UUID.randomUUID().toString();
    private final String eventType = "TEST_EVENT";
    private final int ERROR_MAX_LENGTH = 2000;

    @BeforeEach
    void setUp() {
        // 핸들러 기본 설정
        lenient().when(testEventHandler.eventType()).thenReturn(eventType);
        lenient().when(testEventHandler.payloadType()).thenReturn(TestPayload.class);
        lenient().when(inboxProcessingProps.errorMaxLength()).thenReturn(ERROR_MAX_LENGTH);

        dispatcher = new InboxEventDispatcher(
                List.of(testEventHandler),
                recordRepository,
                objectMapper,
                inboxStatusMarker,
                inboxProcessingProps
        );
    }

    @Test
    @DisplayName("이벤트가 성공적으로 처리되고 leaseId를 사용해 PROCESSED 마킹")
    void processEventSuccessfully() throws JsonProcessingException {
        // given
        TestPayload payload = new TestPayload("Success");
        String payloadJson = objectMapper.writeValueAsString(payload);
        InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson);

        when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

        // when
        dispatcher.processEvent(eventId, leaseId);

        // then
        ArgumentCaptor<TestPayload> argumentCaptor = ArgumentCaptor.forClass(TestPayload.class);
        verify(testEventHandler).handle(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().message()).isEqualTo("Success");

        verify(inboxStatusMarker).markProcessed(eventId, leaseId);
    }

    @Test
    @DisplayName("레코드를 찾을 수 없으면 아무 동작도 하지 않음")
    void shouldDoNothingWhenRecordNotFound() {
        // given
        when(recordRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        // when
        dispatcher.processEvent(eventId, leaseId);

        // then
        verify(testEventHandler, never()).handle(any());
        verifyNoInteractions(inboxStatusMarker);
    }

    @Test
    @DisplayName("이벤트 핸들러가 없으면 DLT로 마킹")
    void shouldMarkDeadLetterWhenNoHandlerFound() throws JsonProcessingException {
        // given
        String unknownEventType = "UnknownEventType";
        TestPayload payload = new TestPayload("Success");
        InboxEventRecord record = createTestRecord(eventId, unknownEventType, objectMapper.writeValueAsString(payload));

        when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

        // when
        dispatcher.processEvent(eventId, leaseId);

        // then
        String expectedReason = abbreviate("NO_HANDLER:" + unknownEventType, ERROR_MAX_LENGTH);
        verify(inboxStatusMarker).markDeadLetter(eventId, leaseId, expectedReason);

        verify(testEventHandler, never()).handle(any());
        verifyNoMoreInteractions(inboxStatusMarker);

    }

    @Test
    @DisplayName("JSON 역직렬화 실패 시 DLT로 마킹하고 RuntimeException 발생")
    void shouldMarkDeadLetterOnDeserializationError() {
        // given
        String malformedJson = "{message: \"hello\"}";
        InboxEventRecord record = createTestRecord(eventId, eventType, malformedJson);

        when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

        // when
        assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("JSON parsing failed, rolling back T1")
                .hasCauseInstanceOf(JsonProcessingException.class);

        // then
        verify(inboxStatusMarker).markDeadLetter(eq(eventId), eq(leaseId), contains("DESERIALIZATION_ERROR:"));
        verify(testEventHandler, never()).handle(any());
    }

    @Test
    @DisplayName("비즈니스 예외(재시도 불가) 발생 시 DLT로 마킹하고 예외 전파")
    void shouldMarkDeadLetterOnBusinessNotRetryableException() throws JsonProcessingException {
        // given
        String errMessage = "biz error";
        TestPayload payload = new TestPayload("test");
        String payloadJson = objectMapper.writeValueAsString(payload);
        InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson);

        when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));
        doThrow(new BusinessNotRetryableException(errMessage)).when(testEventHandler).handle(any(TestPayload.class));

        // when & then
        assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                .isInstanceOf(BusinessNotRetryableException.class)
                .hasMessage(errMessage);

        verify(inboxStatusMarker).markDeadLetter(eventId, leaseId, errMessage);

    }

    @Test
    @DisplayName("처리 중 일반 예외 발생 시 FAILED 마킹하고 예외 전파")
    void shouldMarkFailedOnGenericException() throws JsonProcessingException {
        // given
        String errMessage = "error";
        RuntimeException runtimeException = new RuntimeException(errMessage);
        TestPayload payload = new TestPayload("test");
        String payloadJson = objectMapper.writeValueAsString(payload);
        InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson);

        when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));
        doThrow(runtimeException).when(testEventHandler).handle(any(TestPayload.class));
        // when
        assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(errMessage);

        String reason = abbreviate(runtimeException.getClass().getSimpleName() + ": " +
                Objects.toString(runtimeException.getMessage(), runtimeException.toString()),ERROR_MAX_LENGTH);
        verify(inboxStatusMarker).markFailed(eventId, leaseId, reason);
    }

    @Test
    @DisplayName("낙관적 락 예외 발생 시 아무것도 마킹하지 않고 예외 전파")
    void shouldNotMarkAnythingOnOptimisticLockingFailure() throws JsonProcessingException {
        // given
        String errMessage = "낙관적 락 예외";
        TestPayload payload = new TestPayload("test");
        String payloadJson = objectMapper.writeValueAsString(payload);
        InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson);

        when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));
        doThrow(new OptimisticLockingFailureException(errMessage)).when(testEventHandler).handle(any(TestPayload.class));
        // when
        assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessage(errMessage);

        verifyNoInteractions(inboxStatusMarker);

    }

    @Test
    @DisplayName("Payload가 JSON 'null'일 경우 DLT 마킹 (dispatch null check)")
    void shouldMarkDeadLetterWhenPayloadIsJsonNull() throws JsonProcessingException {
        // given
        // ObjectMapper.readValue("null", TestPayload.class)는 null 객체를 반환함. 좀 특이한듯 "null"은 null객체 반환해줌
        String payloadJson = "null";
        InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson);

        when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

        // when & then
        assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                .isInstanceOf(BusinessNotRetryableException.class)
                .hasMessageContaining("Payload is null: expected=");

        // then
        // processEvent의 바깥쪽 catch (BusinessNotRetryableException) 블록이 실행됨
        verify(inboxStatusMarker).markDeadLetter(eq(eventId), eq(leaseId), contains("Payload is null: expected="));
        verify(testEventHandler, never()).handle(any()); // 핸들러는 호출되면 안 됨
        verifyNoMoreInteractions(inboxStatusMarker);
    }


    private InboxEventRecord createTestRecord(Long eventId, String eventType, String payloadJson) {
        return InboxEventRecord.builder()
                .id(eventId)
                .eventId(eventId)
                .aggregateId(100L)
                .eventType(eventType)
                .payload(payloadJson)
                .inboxEventRecordStatus(InboxEventRecordStatus.PROCESSING)
                .leaseId(leaseId)
                .leaseUntil(LocalDateTime.now(fixedClock).plusMinutes(5))
                .workerId("test-worker")
                .createdAt(LocalDateTime.now(fixedClock))
                .build();
    }


}