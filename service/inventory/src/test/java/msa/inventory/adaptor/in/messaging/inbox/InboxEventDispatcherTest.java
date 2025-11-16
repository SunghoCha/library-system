package msa.inventory.adaptor.in.messaging.inbox;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import msa.common.config.properties.InboxProcessingProps;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.exception.BusinessNotRetryableException;
import msa.common.util.EventPayloadValidator;
import msa.inventory.adaptor.in.messaging.inbox.handler.InboxEventHandler;
import msa.inventory.adaptor.out.persistence.inbox.entity.InboxEventRecord;
import msa.inventory.adaptor.out.persistence.inbox.recorder.InboxStatusMarker;
import msa.inventory.adaptor.out.persistence.inbox.repository.InboxEventRecordRepository;
import msa.inventory.testsupport.time.TestClocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

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
    private EventPayloadValidator validator;

    @Mock
    private InboxStatusMarker inboxStatusMarker;

    @Mock
    private InboxProcessingProps inboxProcessingProps;

    @Mock
    private InboxEventHandler<TestPayload> testEventHandler;

    private final Clock fixedClock = TestClocks.FIXED_CLOCK;

    private final ObjectMapper objectMapper = new Jackson2ObjectMapperBuilder().build();

    record TestPayload(String message) {
    }

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
        lenient().doNothing().when(validator).validateOrThrow(any());

        dispatcher = new InboxEventDispatcher(
                objectMapper,
                validator,
                inboxStatusMarker,
                inboxProcessingProps,
                recordRepository,
                List.of(testEventHandler)
        );
    }

    @Nested
    @DisplayName("성공 케이스")
    class When_Processing_Succeeds {

        @Test
        @DisplayName("이벤트가 성공적으로 처리되고 leaseId를 사용해 PROCESSED 마킹")
        void processEventSuccessfully() throws JsonProcessingException {
            // given
            TestPayload payload = new TestPayload("Success");
            String payloadJson = objectMapper.writeValueAsString(payload);
            InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson, 100L, 1L);

            when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

            // when
            dispatcher.processEvent(eventId, leaseId);

            // then
            ArgumentCaptor<InboxMessage<TestPayload>> argumentCaptor = ArgumentCaptor.captor();
            verify(testEventHandler).handle(argumentCaptor.capture());
            assertThat(argumentCaptor.getValue().payload().message()).isEqualTo("Success");

            verify(inboxStatusMarker).markProcessed(eventId, leaseId);
        }

        @Test
        @DisplayName("validator 가 핸들러보다 먼저 호출된다")
        void validatorShouldBeCalledBeforeHandler() throws JsonProcessingException {
            // given
            TestPayload payload = new TestPayload("Success");
            String payloadJson = objectMapper.writeValueAsString(payload);
            InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson, 100L, 1L);

            when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

            // when
            dispatcher.processEvent(eventId, leaseId);

            // then
            // 순서 검증
            InOrder inOrder = inOrder(validator, testEventHandler);
            inOrder.verify(validator).validateOrThrow(any(TestPayload.class));
            inOrder.verify(testEventHandler).handle(any());

        }

        @Test
        @DisplayName("dispatch 시 InboxMessage 에 eventId, aggregateId, aggregateVersion, eventType 이 그대로 전달된다")
        void dispatchShouldPassAllMetadataToHandler() throws JsonProcessingException {
            // given
            long aggregateId = 123L;
            long aggregateVersion = 4L;
            TestPayload payload = new TestPayload("metadata test");
            String payloadJson = objectMapper.writeValueAsString(payload);
            InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson, aggregateId, aggregateVersion);
            when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

            // when
            dispatcher.processEvent(eventId, leaseId);

            // then
            ArgumentCaptor<InboxMessage<TestPayload>> messageCaptor = ArgumentCaptor.captor();
            verify(testEventHandler).handle(messageCaptor.capture());

            InboxMessage<TestPayload> capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.payload().message()).isEqualTo("metadata test");
            assertThat(capturedMessage.eventId()).isEqualTo(eventId);
            assertThat(capturedMessage.aggregateId()).isEqualTo(aggregateId);
            assertThat(capturedMessage.aggregateVersion()).isEqualTo(aggregateVersion);
            assertThat(capturedMessage.eventType()).isEqualTo(eventType);

        }
    }

    @Nested
    @DisplayName("스킵/무시 케이스 (상태 변경 없음)")
    class When_Event_Is_Skipped {

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
        @DisplayName("낙관적 락 예외 발생 시 아무것도 마킹하지 않고 예외 전파")
        void shouldNotMarkAnythingOnOptimisticLockingFailure() throws JsonProcessingException {
            // given
            String errMessage = "낙관적 락 예외";
            TestPayload payload = new TestPayload("test");
            String payloadJson = objectMapper.writeValueAsString(payload);
            InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson, 100L, 1L);

            when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));
            doThrow(new OptimisticLockingFailureException(errMessage)).when(testEventHandler).handle(any());
            // when
            assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                    .isInstanceOf(OptimisticLockingFailureException.class)
                    .hasMessage(errMessage);

            verifyNoInteractions(inboxStatusMarker);

        }
    }

    @Nested
    @DisplayName("DEAD_LETTER 마킹 케이스 (재시도 불가)")
    class When_Marking_As_DeadLetter {

        @Test
        @DisplayName("이벤트 핸들러가 없으면 DLT로 마킹")
        void shouldMarkDeadLetterWhenNoHandlerFound() throws JsonProcessingException {
            // given
            String unknownEventType = "UnknownEventType";
            TestPayload payload = new TestPayload("Success");
            InboxEventRecord record = createTestRecord(eventId, unknownEventType,
                    objectMapper.writeValueAsString(payload), 100L, 1L);

            when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

            // when
            dispatcher.processEvent(eventId, leaseId);

            // then
            String expectedReason = abbreviate("NO_HANDLER: " + unknownEventType, ERROR_MAX_LENGTH);
            verify(inboxStatusMarker).markDeadLetter(eventId, leaseId, expectedReason);

            verify(testEventHandler, never()).handle(any());
            verifyNoMoreInteractions(inboxStatusMarker);

        }

        @Test
        @DisplayName("[신규] eventType 이 null 이면 NO_EVENT_TYPE 으로 DLT 마킹")
        void shouldMarkDeadLetterWhenEventTypeIsNull() throws JsonProcessingException {
            // given
            TestPayload payload = new TestPayload("no event type");
            String payloadJson = objectMapper.writeValueAsString(payload);
            InboxEventRecord record = createTestRecord(eventId, null, payloadJson, 100L, 1L);
            when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));
            String expectedReason = abbreviate("NO_EVENT_TYPE: " + null, ERROR_MAX_LENGTH);
            // when
            dispatcher.processEvent(eventId, leaseId);

            // then
            verify(inboxStatusMarker).markDeadLetter(eventId, leaseId, expectedReason);
            verify(testEventHandler, never()).handle(any());

        }

        @Test
        @DisplayName("[신규] eventType 이 공백(blank) 이면 NO_EVENT_TYPE 으로 DLT 마킹")
        void shouldMarkDeadLetterWhenEventTypeIsBlank() throws JsonProcessingException {
            // given
            String blankEventType = " ";
            TestPayload payload = new TestPayload("blank event type");
            String payloadJson = objectMapper.writeValueAsString(payload);
            InboxEventRecord record = createTestRecord(eventId, blankEventType, payloadJson, 100L, 1L);
            when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));
            String expectedReason = abbreviate("NO_EVENT_TYPE: " + blankEventType, ERROR_MAX_LENGTH);

            // when
            dispatcher.processEvent(eventId, leaseId);

            // then
            verify(inboxStatusMarker).markDeadLetter(eventId, leaseId, expectedReason);
            verify(testEventHandler, never()).handle(any());
        }

        @Test
        @DisplayName("[신규] validator 가 ConstraintViolationException 을 던지면 DLT로 마킹하고 핸들러는 호출 안 함")
        void shouldMarkDeadLetterWhenValidatorFails() throws JsonProcessingException {
            // given
            TestPayload payload = new TestPayload("invalid payload");
            String payloadJson = objectMapper.writeValueAsString(payload);
            InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson, 100L, 1L);
            when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

            String validationMsg = "Payload empty";
            ConstraintViolationException cvEx = new ConstraintViolationException(validationMsg, Set.of());
            doThrow(cvEx).when(validator).validateOrThrow(any(TestPayload.class));

            String expectedReason = abbreviate(cvEx.getMessage(), ERROR_MAX_LENGTH);

            // when
            dispatcher.processEvent(eventId, leaseId);

            // then
            verify(inboxStatusMarker).markDeadLetter(eventId, leaseId, expectedReason);
            verify(testEventHandler, never()).handle(any());

        }

        @Test
        @DisplayName("JSON 역직렬화 실패 시 DLT로 마킹하고 RuntimeException 발생")
        void shouldMarkDeadLetterOnDeserializationError() {
            // given
            String malformedJson = "{message: \"hello\"}";
            InboxEventRecord record = createTestRecord(eventId, eventType, malformedJson, 100L, 1L);

            when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));

            // when
            assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("JSON parsing failed, rolling back")
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
            InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson, 100L, 1L);

            when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));
            doThrow(new BusinessNotRetryableException(errMessage)).when(testEventHandler).handle(any());

            // when & then
            assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                    .isInstanceOf(BusinessNotRetryableException.class)
                    .hasMessage(errMessage);

            verify(inboxStatusMarker).markDeadLetter(eventId, leaseId, errMessage);

        }

        @Test
        @DisplayName("Payload가 JSON 'null'일 경우 DLT 마킹 (dispatch null check)")
        void shouldMarkDeadLetterWhenPayloadIsJsonNull() {
            // given
            // ObjectMapper.readValue("null", TestPayload.class)는 null 객체를 반환함. 좀 특이한듯 "null"은 null객체 반환해줌
            String payloadJson = "null";
            InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson, 100L, 1L);

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

    }

    @Nested
    @DisplayName("FAILED 마킹 케이스 (재시도 가능)")
    class When_Marking_As_Failed {

        @Test
        @DisplayName("처리 중 일반 예외 발생 시 FAILED 마킹하고 예외 전파")
        void shouldMarkFailedOnGenericException() throws JsonProcessingException {
            // given
            String errMessage = "error";
            RuntimeException runtimeException = new RuntimeException(errMessage);
            TestPayload payload = new TestPayload("test");
            String payloadJson = objectMapper.writeValueAsString(payload);
            InboxEventRecord record = createTestRecord(eventId, eventType, payloadJson, 100L, 1L);

            when(recordRepository.findByEventId(eventId)).thenReturn(Optional.of(record));
            doThrow(runtimeException).when(testEventHandler).handle(any());
            // when
            assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage(errMessage);

            String reason = abbreviate(runtimeException.getClass().getSimpleName() + ": " +
                    Objects.toString(runtimeException.getMessage(), runtimeException.toString()), ERROR_MAX_LENGTH);
            verify(inboxStatusMarker).markFailed(eventId, leaseId, reason);
        }

    }


    private InboxEventRecord createTestRecord(Long eventId, String eventType, String payloadJson,
                                              Long aggregateId, Long aggregateVersion) {
        return InboxEventRecord.builder()
                .id(eventId)
                .eventId(eventId)
                .aggregateId(aggregateId) // [수정]
                .aggregateVersion(aggregateVersion)
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